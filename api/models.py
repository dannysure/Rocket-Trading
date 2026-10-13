"""
Pydantic response models for API endpoints
"""

from pydantic import BaseModel
from datetime import datetime
from typing import Optional, List
from decimal import Decimal


class HealthCheckResponse(BaseModel):
    """Health check response"""
    service: str
    status: str  # 'active' or 'degraded'
    database: str  # 'connected' or 'disconnected'
    version: str
    timestamp: Optional[datetime] = None


class KPIPanelResponse(BaseModel):
    """24-hour KPI metrics"""
    total_clients: int
    total_accounts: int
    total_instruments: int
    total_orders: int
    total_fills: int
    fill_rate: float
    total_gmv: Decimal
    timestamp: datetime


class PlatformOverviewResponse(BaseModel):
    """Global platform statistics"""
    total_clients: int
    total_accounts: int
    total_orders: int
    total_fills: int
    fill_rate: float
    total_gmv: Decimal
    avg_order_size: Decimal
    active_instruments: int
    top_symbols: List[dict]


class OrderLifecycleResponse(BaseModel):
    """Complete order lifecycle with audit trail"""
    order_id: int
    client_id: int
    symbol: str
    status: str
    submitted_at: datetime
    accepted_at: Optional[datetime]
    filled_at: Optional[datetime]
    rejected_at: Optional[datetime]
    rejection_reason: Optional[str]
    events: List[dict]


class PortfolioPerformanceResponse(BaseModel):
    """Portfolio performance metrics"""
    client_id: int
    total_portfolio_value: Decimal
    total_cash: Decimal
    total_holdings_value: Decimal
    daily_pnl: Decimal
    daily_pnl_pct: float


class RiskProfileResponse(BaseModel):
    """Risk profile for a client"""
    client_id: int
    concentration_risk: float
    volatility: float
    leverage: float
    var_95: Decimal


class AlertResponse(BaseModel):
    """Risk alert"""
    alert_id: int
    client_id: int
    alert_type: str  # 'HIGH_CONCENTRATION', 'HIGH_VOLATILITY', etc.
    severity: str  # 'Low', 'Medium', 'High'
    message: str
    created_at: datetime
