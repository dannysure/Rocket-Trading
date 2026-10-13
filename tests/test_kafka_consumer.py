from unittest.mock import Mock

from api.kafka_consumer import AnalyticsKafkaConsumer


def test_duplicate_filled_event_updates_analytics_once():
    redis_client = Mock()
    redis_client.exists.side_effect = [False, True]
    consumer = AnalyticsKafkaConsumer("kafka:9092", redis_client)
    message = Mock()
    message.topic.return_value = "order.filled"
    message.value.return_value = (
        b'{"fill_id": 7, "client_id": 42, "symbol": "AAPL", '
        b'"executed_quantity": 2, "executed_price": 10}'
    )

    consumer.process_message(message)
    consumer.process_message(message)

    assert redis_client.incr.call_count == 6
    assert redis_client.incrbyfloat.call_count == 3
    assert redis_client.zincrby.call_count == 6
    assert redis_client.set.call_count == 1
