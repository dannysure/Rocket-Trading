"""
Pydantic response models for type-safe API responses
Maps database queries to structured response schemas
"""
from pydantic import BaseModel, Field
from typing import List, Optional
from decimal import Decimal
from datetime import datetime



class HoldingResponse(BaseModel):
    """Individual security holding"""
    instrument_id: int
    instrument_name: str
    asset_class: str
    quantity: int
    current_price: Decimal
    total_value: Decimal = Field(..., description="quantity * current_price")
    
    class Config:
        json_encoders = {Decimal: float}

class PortfolioPerformanceResponse(BaseModel):
    """Portfolio performance for single client"""
    client_id: int
    total_cash: Decimal
    total_holdings_value: Decimal
    total_portfolio_value: Decimal = Field(..., description="cash + holdings")
    holdings: List[HoldingResponse]
    
    class Config:
        json_encoders = {Decimal: float}

class RiskProfileResponse(BaseModel):
    """Client risk profile analysis"""
    client_id: int
    risk_level: str  # Cautious, Balanced, Adventurous
    equity_allocation_pct: float
    fx_allocation_pct: float
    crypto_allocation_pct: float
    total_value: Decimal
    
    class Config:
        json_encoders = {Decimal: float}



class FillResponse(BaseModel):
    """Individual fill execution"""
    fill_id: int
    order_id: int
    executed_quantity: int
    executed_price: Decimal
    executed_at: datetime
    
    class Config:
        json_encoders = {Decimal: float}

class OrderResponse(BaseModel):
    """Order with fills"""
    order_id: int
    instrument_name: str
    order_side: str  # BUY, SELL
    order_type: str  # MARKET, LIMIT
    requested_quantity: int
    order_status: str  # SUBMITTED, ACCEPTED, FILLED, REJECTED, CANCELLED
    created_at: datetime
    fills: List[FillResponse]
    
    class Config:
        json_encoders = {Decimal: float}

class TradingActivityResponse(BaseModel):
    """Trading activity metrics"""
    total_orders: int
    buy_orders: int
    sell_orders: int
    filled_orders: int
    rejected_orders: int
    fill_rate_pct: float
    total_shares_traded: int
    
    class Config:
        json_encoders = {Decimal: float}



class KPIMetricResponse(BaseModel):
    """Single KPI metric"""
    metric_name: str
    value: float
    timestamp: datetime
    unit: Optional[str] = None

class KPIPanelResponse(BaseModel):
    """24-hour KPI dashboard"""
    metrics: List[KPIMetricResponse]
    summary: str
    
    class Config:
        json_encoders = {Decimal: float}

class RiskAlertResponse(BaseModel):
    """Risk alert for client or account"""
    alert_id: int
    client_id: int
    alert_type: str  # HighLeverage, Concentration, LargeLoss, etc
    severity: str  # Critical, High, Medium, Low
    message: str
    created_at: datetime

class AuditLogResponse(BaseModel):
    """Audit trail entry"""
    audit_id: int
    entity_name: str
    entity_id: int
    action_type: str  # INSERT, UPDATE, DELETE
    performed_by: Optional[str] = None
    performed_at: datetime
    state_before: Optional[dict] = None
    state_after: Optional[dict] = None

class OrderLifecycleResponse(BaseModel):
    """Complete order traceability"""
    order_id: int
    client_id: int
    instrument_name: str
    order_side: str
    order_type: str
    requested_quantity: int
    current_status: str
    created_at: datetime
    filled_at: Optional[datetime] = None
    rejection_reason: Optional[str] = None
    fills: List[FillResponse]
    audit_entries: List[AuditLogResponse]
    
    class Config:
        json_encoders = {Decimal: float}



class WatchlistItemResponse(BaseModel):
    """Item on client watchlist"""
    instrument_id: int
    instrument_name: str
    current_price: Decimal
    alert_price_buy: Optional[Decimal] = None
    alert_price_sell: Optional[Decimal] = None
    added_at: datetime
    
    class Config:
        json_encoders = {Decimal: float}

class WatchlistResponse(BaseModel):
    """Client watchlist"""
    client_id: int
    items: List[WatchlistItemResponse]



class MarketTrendResponse(BaseModel):
    """Market trend analysis"""
    instrument_id: int
    instrument_name: str
    popularity_score: int  # Orders in last 30 days
    trend: str  # Up, Down, Neutral
    price_change_pct: float
    
    class Config:
        json_encoders = {Decimal: float}

class AssetAllocationResponse(BaseModel):
    """Asset class distribution"""
    asset_class: str
    quantity: int
    total_value: Decimal
    allocation_pct: float
    
    class Config:
        json_encoders = {Decimal: float}



class AccountPerformanceResponse(BaseModel):
    """Account performance metrics"""
    account_id: int
    account_type: str  # ISA, GIA, SIPP, DIRECT_TRADING
    cash_balance: Decimal
    total_value: Decimal
    performance_pct: float
    
    class Config:
        json_encoders = {Decimal: float}



class PlatformOverviewResponse(BaseModel):
    """Global platform statistics"""
    total_clients: int
    total_accounts: int
    total_instruments: int
    total_orders: int
    total_fills: int
    total_cash_managed: Decimal
    total_portfolio_value: Decimal
    avg_fill_rate_pct: float
    
    class Config:
        json_encoders = {Decimal: float}

class HealthCheckResponse(BaseModel):
    """Service health status"""
    service: str
    status: str  # active, degraded, offline
    database: str  # connected, disconnected
    timestamp: datetime
