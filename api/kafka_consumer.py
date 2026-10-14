"""
Kafka consumer for analytics events
Subscribes to order.filled and order.rejected topics
Updates Redis cache with KPI metrics
"""

import json
import logging
import time
from datetime import datetime
from confluent_kafka import Consumer, KafkaError
import redis

logger = logging.getLogger(__name__)


class AnalyticsKafkaConsumer:
    """
    Consumes Kafka events and updates analytics cache in Redis
    - Listens to order.filled events
    - Updates KPI cache: fill count, GMV volume, top clients
    - Supports horizontal scaling via consumer groups
    """

    def __init__(self, bootstrap_servers: str, redis_client: redis.Redis):
        """
        Initialize Kafka consumer and Redis connection
        
        Args:
            bootstrap_servers: Kafka broker addresses (e.g., 'kafka:9092')
            redis_client: Redis connection instance
        """
        self.redis = redis_client
        self.bootstrap_servers = bootstrap_servers
        self.consumer = None
        self.running = False
        self.retry_delay_seconds = 1
        self.processed_event_ttl_seconds = 90 * 24 * 60 * 60

    def connect(self):
        """Connect to Kafka broker"""
        try:
            conf = {
                'bootstrap.servers': self.bootstrap_servers,
                'group.id': 'analytics-consumer-group',
                'auto.offset.reset': 'earliest',
                'enable.auto.commit': False,
                'session.timeout.ms': 30000,
                'client.id': 'analytics-consumer-1'
            }
            consumer = Consumer(conf)
            consumer.subscribe(['order.filled', 'order.rejected'])
            consumer.list_topics(timeout=5)
            self.consumer = consumer
            logger.info(f"✅ Connected to Kafka: {self.bootstrap_servers}")
        except Exception as e:
            if 'consumer' in locals():
                consumer.close()
            logger.error(f"❌ Failed to connect to Kafka: {e}")
            raise

    def start(self):
        """Start consuming events (blocking)"""
        self.running = True
        logger.info("🚀 Starting analytics consumer...")

        try:
            while self.running:
                if self.consumer is None:
                    try:
                        self.connect()
                        self.retry_delay_seconds = 1
                    except Exception:
                        logger.warning(
                            "Kafka unavailable; retrying in %s seconds",
                            self.retry_delay_seconds,
                        )
                        time.sleep(self.retry_delay_seconds)
                        self.retry_delay_seconds = min(self.retry_delay_seconds * 2, 30)
                        continue

                msg = self.consumer.poll(timeout=1.0)

                if msg is None:
                    continue

                if msg.error():
                    if msg.error().code() == KafkaError._PARTITION_EOF:
                        logger.debug("Reached end of partition")
                    else:
                        logger.error(f"Kafka error: {msg.error()}")
                        self.consumer.close()
                        self.consumer = None
                    continue

                try:
                    self.process_message(msg)
                    self.consumer.commit(message=msg, asynchronous=False)
                except Exception as e:
                    logger.error(
                        "❌ Event processing failed; offset will be retried: %s", e
                    )

        except KeyboardInterrupt:
            logger.info("Consumer interrupted by user")
        finally:
            self.stop()

    def stop(self):
        """Stop consuming and close connection"""
        self.running = False
        if self.consumer:
            self.consumer.close()
            self.consumer = None
        logger.info("🛑 Analytics consumer stopped")

    def process_message(self, msg):
        """
        Process a Kafka message and update Redis cache
        
        Args:
            msg: Kafka message with order.filled or order.rejected event
        """
        topic = msg.topic()
        value = json.loads(msg.value().decode('utf-8'))
        event_key = self._event_key(topic, value)
        processed_key = f"kpi:processed:{topic}:{event_key}"

        if self.redis.exists(processed_key):
            logger.debug("Skipping already processed event %s", processed_key)
            return

        if topic == 'order.filled':
            self.on_order_filled(value)
        elif topic == 'order.rejected':
            self.on_order_rejected(value)

        self.redis.set(processed_key, '1', ex=self.processed_event_ttl_seconds)
        logger.debug(f"✅ Processed {topic}: event={event_key}")

    @staticmethod
    def _event_key(topic: str, event: dict) -> str:
        if topic == 'order.filled':
            return str(event.get('fill_id', event.get('fillId', event.get('order_id', event.get('orderId')))))
        return f"{event.get('order_id', event.get('orderId'))}:{event.get('rejection_stage', event.get('rejectionStage', 'UNKNOWN'))}"

    def on_order_filled(self, event: dict):
        """
        Handle order.filled event
        Updates: fill count, GMV, client statistics
        
        Event schema:
        {
            "orderId": 123,
            "clientId": 1,
            "symbol": "AAPL",
            "executedQuantity": 100,
            "executedPrice": 150.50,
            "executedAt": "2026-10-12T19:00:00Z"
        }
        """
        try:
            client_id = event.get('client_id', event.get('clientId'))
            symbol = event.get('symbol')
            quantity = float(event.get('executed_quantity', event.get('executedQuantity', 0)))
            price = float(event.get('executed_price', event.get('executedPrice', 0)))
            gmv = quantity * price

            # Keep the same event in each window used by the analytics API.
            now = datetime.utcnow()

            periods = (
                ('minute', now.strftime('%Y%m%d%H%M'), 3600),
                ('hour', now.strftime('%Y%m%d%H'), 86400),
                ('day', now.strftime('%Y%m%d'), 604800),
            )
            for period, period_key, expiration in periods:
                fills_key = f"kpi:fills:{period}:{period_key}"
                gmv_key = f"kpi:gmv:{period}:{period_key}"
                symbols_key = f"kpi:top-symbols:{period}:{period_key}"
                clients_key = f"kpi:top-clients:{period}:{period_key}"

                self.redis.incr(fills_key)
                self.redis.incrbyfloat(gmv_key, gmv)
                self.redis.zincrby(symbols_key, gmv, symbol)
                self.redis.zincrby(clients_key, gmv, f"client:{client_id}")
                self.redis.incr(f"kpi:client:{client_id}:fills:{period_key}")

                for key in (fills_key, gmv_key, symbols_key, clients_key,
                            f"kpi:client:{client_id}:fills:{period_key}"):
                    self.redis.expire(key, expiration)

            logger.debug(f"Updated KPI for fill: {symbol} {quantity} @ {price}")

        except Exception as e:
            logger.error(f"Error updating KPI on order filled: {e}")
            raise

    def on_order_rejected(self, event: dict):
        """
        Handle order.rejected event
        Updates: rejection statistics
        
        Event schema:
        {
            "orderId": 123,
            "clientId": 1,
            "symbol": "AAPL",
            "rejectionReason": "INSUFFICIENT_BALANCE",
            "rejectedAt": "2026-10-12T19:00:00Z"
        }
        """
        try:
            now = datetime.utcnow()
            reason = event.get('rejection_reason', event.get('rejectionReason', 'UNKNOWN'))

            periods = (
                ('minute', now.strftime('%Y%m%d%H%M'), 3600),
                ('hour', now.strftime('%Y%m%d%H'), 86400),
                ('day', now.strftime('%Y%m%d'), 604800),
            )
            for period, period_key, expiration in periods:
                rejections_key = f"kpi:rejections:{period}:{period_key}"
                self.redis.incr(f"{rejections_key}:total")
                self.redis.incr(f"{rejections_key}:{reason}")
                self.redis.expire(f"{rejections_key}:total", expiration)
                self.redis.expire(f"{rejections_key}:{reason}", expiration)

            logger.debug(f"Tracked rejection: {reason}")

        except Exception as e:
            logger.error(f"Error updating rejection stats: {e}")
            raise

    def get_kpi_summary(self, time_period: str = "day") -> dict:
        """
        Get KPI summary from Redis cache
        
        Args:
            time_period: 'minute', 'hour', or 'day'
        
        Returns:
            Dictionary with KPI metrics
        """
        try:
            now = datetime.utcnow()

            if time_period == "day":
                period_str = now.strftime('%Y%m%d')
            elif time_period == "hour":
                period_str = now.strftime('%Y%m%d%H')
            elif time_period == "minute":
                period_str = now.strftime('%Y%m%d%H%M')
            else:
                period_str = now.strftime('%Y%m%d')

            fills = int(self.redis.get(f"kpi:fills:{time_period}:{period_str}") or 0)
            gmv = float(self.redis.get(f"kpi:gmv:{time_period}:{period_str}") or 0)

            return {
                'time_period': time_period,
                'fills': fills,
                'gmv': gmv,
                'avg_fill_size': gmv / fills if fills > 0 else 0
            }

        except Exception as e:
            logger.error(f"Error getting KPI summary: {e}")
            return {}


# Standalone consumer for testing
if __name__ == "__main__":
    import os
    from dotenv import load_dotenv

    load_dotenv()

    kafka_broker = os.getenv('KAFKA_BOOTSTRAP_SERVERS', 'kafka:9092')
    redis_host = os.getenv('REDIS_HOST', 'localhost')
    redis_port = int(os.getenv('REDIS_PORT', 6379))

    redis_client = redis.Redis(host=redis_host, port=redis_port, decode_responses=True)
    consumer = AnalyticsKafkaConsumer(kafka_broker, redis_client)

    try:
        consumer.connect()
        consumer.start()
    except Exception as e:
        logger.error(f"Consumer failed: {e}")
