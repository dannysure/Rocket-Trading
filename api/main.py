"""
Rocket Trading Analytics API
FastAPI service for trading analytics and reporting
Consumes Kafka events and caches KPIs in Redis
"""

import logging
import os
import threading
from contextlib import asynccontextmanager
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
import redis

from api.database import test_connection
from api.models import HealthCheckResponse
from api.routes import analytics
from api.kafka_consumer import AnalyticsKafkaConsumer

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Global Kafka consumer instance
kafka_consumer = None
kafka_consumer_thread = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    """
    Application lifecycle management
    - Startup: Initialize Kafka consumer, Redis connection
    - Shutdown: Gracefully close connections
    """
    global kafka_consumer, kafka_consumer_thread
    
    logger.info("🚀 Starting Rocket Trading Analytics API...")
    
    # Initialize Kafka consumer
    try:
        kafka_bootstrap = os.getenv('KAFKA_BOOTSTRAP_SERVERS', 'kafka:9092')
        redis_host = os.getenv('REDIS_HOST', 'localhost')
        redis_port = int(os.getenv('REDIS_PORT', '6379'))
        
        logger.info(f"Connecting to Kafka: {kafka_bootstrap}")
        logger.info(f"Connecting to Redis: {redis_host}:{redis_port}")
        
        # Initialize Redis connection
        redis_client = redis.Redis(host=redis_host, port=redis_port, decode_responses=True)
        try:
            redis_client.ping()
            logger.info("✅ Redis connected")
        except Exception as e:
            logger.warning("Redis unavailable; Kafka events will retry: %s", e)
        
        # Initialize Kafka consumer
        kafka_consumer = AnalyticsKafkaConsumer(kafka_bootstrap, redis_client)
        
        # Start consumer in background thread
        kafka_consumer_thread = threading.Thread(target=kafka_consumer.start, daemon=True)
        kafka_consumer_thread.start()
        logger.info("✅ Kafka consumer started in background thread")
        
    except Exception as e:
        logger.error(f"❌ Failed to initialize Kafka consumer: {e}")
    
    yield
    
    logger.info("🛑 Shutting down Rocket Trading Analytics API...")
    
    # Gracefully shutdown Kafka consumer
    if kafka_consumer:
        try:
            logger.info("Stopping Kafka consumer...")
            kafka_consumer.stop()
            if kafka_consumer_thread:
                kafka_consumer_thread.join(timeout=5)
            logger.info("✅ Kafka consumer stopped")
        except Exception as e:
            logger.error(f"Error stopping Kafka consumer: {e}")


app = FastAPI(
    title="Rocket Trading Analytics Engine",
    description="REST API for trading platform analytics, KPIs, and insights",
    version="1.0.0",
    lifespan=lifespan
)

# CORS middleware for frontend integration
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # Configure based on deployment
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Include routers
app.include_router(analytics.router)


# ============================================================================
# HEALTH CHECK ENDPOINTS
# ============================================================================

@app.get("/health", response_model=HealthCheckResponse)
def health_check():
    """Service health status"""
    db_connected = test_connection()
    return HealthCheckResponse(
        service="Rocket Trading Analytics",
        status="active" if db_connected else "degraded",
        database="connected" if db_connected else "disconnected",
        version="1.0.0"
    )


@app.get("/")
def root():
    """API root - redirect to docs"""
    return {
        "service": "Rocket Trading Analytics Engine",
        "version": "1.0.0",
        "docs": "/docs",
        "openapi": "/openapi.json",
        "health": "/health"
    }


# ============================================================================
# ERROR HANDLERS
# ============================================================================

@app.exception_handler(ValueError)
async def value_error_handler(request, exc):
    from fastapi.responses import JSONResponse
    return JSONResponse(
        status_code=400,
        content={"detail": str(exc)}
    )


@app.exception_handler(Exception)
async def general_exception_handler(request, exc):
    from fastapi.responses import JSONResponse
    logger.error(f"Unhandled exception: {exc}")
    return JSONResponse(
        status_code=500,
        content={"detail": "Internal server error"}
    )


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        app,
        host="0.0.0.0",
        port=8000,
        workers=4,
        access_log=True
    )
