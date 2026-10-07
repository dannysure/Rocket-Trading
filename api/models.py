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


class DirectTradingAccountResponse(BaseModel):
    """Direct-trading account available for perpetual futures."""
    account_id: int
    client_id: int
    client_name: str
    account_type: str
    currency: str
    cash_balance: Decimal

    class Config:
        json_encoders = {Decimal: float}


class PerpetualPositionResponse(BaseModel):
    """Current perpetual position for one account and market."""
    market_symbol: str
    side: str
    signed_quantity: Decimal
    entry_price: Decimal
    mark_price: Decimal
    notional_usd: Decimal
    leverage: int
    liquidation_price: Decimal
    unrealized_pnl_usd: Decimal
    realized_pnl_usd: Decimal
    cumulative_funding_usd: Decimal
    initial_margin_usd: Decimal
    maintenance_margin_usd: Decimal
    updated_at: Optional[datetime] = None

    class Config:
        json_encoders = {Decimal: float}


class PerpetualOrderFillResponse(BaseModel):
    """Execution detail for a perpetual order."""
    fill_id: int
    order_id: int
    quantity: Decimal
    price: Decimal
    fee_usd: Decimal
    realized_pnl_usd: Decimal
    created_at: datetime

    class Config:
        json_encoders = {Decimal: float}


class PerpetualOrderResponse(BaseModel):
    """Perpetual order and fill summary."""
    order_id: int
    market_symbol: str
    side: str
    order_type: str
    reduce_only: bool
    quantity: Decimal
    leverage: int
    limit_price: Optional[Decimal] = None
    status: str
    mark_price: Decimal
    average_fill_price: Optional[Decimal] = None
    notional_usd: Decimal
    fee_usd: Decimal
    rejection_reason: Optional[str] = None
    created_at: datetime
    filled_at: Optional[datetime] = None
    fill: Optional[PerpetualOrderFillResponse] = None

    class Config:
        json_encoders = {Decimal: float}


class PerpetualMarketOverviewResponse(BaseModel):
    """Full Ethereum perpetual market view for the trading page."""
    market_symbol: str
    display_name: str
    asset_symbol: str
    quote_currency: str
    settlement_asset: str
    spot_symbol: str
    trading_view_symbol: str
    index_price: Decimal
    mark_price: Decimal
    best_bid: Decimal
    best_ask: Decimal
    basis_bps: Decimal
    premium_index: Decimal
    funding_rate: Decimal
    annualized_funding_rate: Decimal
    funding_direction: str
    next_funding_at: datetime
    open_interest_usd: Decimal
    long_open_interest_usd: Decimal
    short_open_interest_usd: Decimal
    long_short_ratio: float
    volume_24h_usd: Decimal
    insurance_fund_usd: Decimal
    spot_change_24h_pct: float
    quote_timestamp: datetime
    account_id: int
    client_id: int
    available_collateral_usd: Decimal
    account_equity_usd: Decimal
    maintenance_margin_rate: Decimal
    max_leverage: int
    maker_fee_rate: Decimal
    taker_fee_rate: Decimal
    position: Optional[PerpetualPositionResponse] = None
    recent_orders: List[PerpetualOrderResponse]

    class Config:
        json_encoders = {Decimal: float}


class SubmitPerpetualOrderRequest(BaseModel):
    """Submit a perpetual futures order for immediate execution/rejection."""
    client_id: int
    account_id: int
    market_symbol: str
    side: str
    order_type: str
    quantity: Decimal
    leverage: int
    limit_price: Optional[Decimal] = None
    reduce_only: bool = False


class PerpetualOrderSubmissionResponse(BaseModel):
    """Order result plus refreshed market state."""
    order: PerpetualOrderResponse
    market: PerpetualMarketOverviewResponse

    class Config:
        json_encoders = {Decimal: float}
