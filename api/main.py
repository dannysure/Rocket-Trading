"""
LEAP Analytics REST API
Exposes database queries and analytics as HTTP endpoints
"""
from fastapi import FastAPI, Depends
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from datetime import datetime
from .database import get_db, test_connection
from .models import HealthCheckResponse
from .routes import portfolio, trading, analytics, watchlist

app = FastAPI(
    title="LEAP Analytics Engine",
    description="REST API for trading platform analytics and insights",
    version="1.0.0"
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
app.include_router(portfolio.router)
app.include_router(trading.router)
app.include_router(analytics.router)
app.include_router(watchlist.router)

# ============================================================================
# HEALTH CHECK ENDPOINTS
# ============================================================================

@app.get("/health", response_model=HealthCheckResponse)
def health_check():
    """Service health status"""
    db_connected = test_connection()
    return HealthCheckResponse(
        service="LEAP Analytics",
        status="active" if db_connected else "degraded",
        database="connected" if db_connected else "disconnected",
        timestamp=datetime.now()
    )

@app.get("/")
def root():
    """API root - redirect to docs"""
    return {
        "service": "LEAP Analytics Engine",
        "version": "1.0.0",
        "docs": "/docs",
        "openapi": "/openapi.json"
    }

# ============================================================================
# ERROR HANDLERS
# ============================================================================

@app.exception_handler(ValueError)
async def value_error_handler(request, exc):
    return JSONResponse(
        status_code=400,
        content={"detail": str(exc)}
    )

@app.exception_handler(Exception)
async def general_exception_handler(request, exc):
    return JSONResponse(
        status_code=500,
        content={"detail": "Internal server error"}
    )