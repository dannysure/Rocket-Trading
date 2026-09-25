"""
Query functions for analytics domain
Wraps SQL queries for KPIs, alerts, and dashboard metrics
"""
from sqlalchemy.orm import Session
from sqlalchemy import text
from decimal import Decimal
from datetime import datetime, timedelta
from ..models import (
    KPIPanelResponse, KPIMetricResponse, RiskAlertResponse,
    AuditLogResponse, OrderLifecycleResponse, FillResponse, PlatformOverviewResponse
)
from typing import List, Optional, Dict, Any

def get_kpi_panel(db: Session, client_id: Optional[int] = None) -> KPIPanelResponse:
    """
    ANALYTICS SECTION 11: Real-time KPIs & Alerts
    24-hour metrics: orders created, fills executed, avg fill rate, revenue
    """
    where_clause = f"WHERE cp.client_id = :client_id" if client_id else ""
    params: Dict[str, Any] = {"client_id": client_id} if client_id else {}
    time_threshold = datetime.now() - timedelta(hours=24)
    params["time_threshold"] = time_threshold
    
    query = text(f"""
        SELECT 
            COUNT(DISTINCT o.order_id) as orders_24h,
            COUNT(DISTINCT f.fill_id) as fills_24h,
            SUM(f.executed_quantity) as shares_traded_24h,
            AVG(CASE WHEN o.order_status = 'FILLED' THEN 1 ELSE 0 END) * 100 as fill_rate_24h
        FROM orders o
        LEFT JOIN fills f ON o.order_id = f.order_id AND f.executed_at >= :time_threshold
        LEFT JOIN client_profiles cp ON o.client_id = cp.client_id
        WHERE o.created_at >= :time_threshold
        {" AND cp.client_id = :client_id" if client_id else ""}
    """)
    
    result = db.execute(query, params).fetchone()
    
    if result is None:
        return KPIPanelResponse(
            metrics=[],
            summary="No activity in the last 24 hours"
        )
    
    metrics = [
        KPIMetricResponse(
            metric_name="Orders (24h)",
            value=float(result[0] or 0),
            timestamp=datetime.now(),
            unit="count"
        ),
        KPIMetricResponse(
            metric_name="Fills (24h)",
            value=float(result[1] or 0),
            timestamp=datetime.now(),
            unit="count"
        ),
        KPIMetricResponse(
            metric_name="Shares Traded (24h)",
            value=float(result[2] or 0),
            timestamp=datetime.now(),
            unit="shares"
        ),
        KPIMetricResponse(
            metric_name="Fill Rate (24h)",
            value=float(result[3] or 0),
            timestamp=datetime.now(),
            unit="%"
        )
    ]
    
    return KPIPanelResponse(
        metrics=metrics,
        summary=f"24h Activity: {int(result[0] or 0)} orders, {int(result[1] or 0)} fills"
    )

def get_risk_alerts(
    db: Session,
    client_id: Optional[int] = None,
    severity: Optional[str] = None
) -> List[RiskAlertResponse]:
    """
    ANALYTICS SECTION 11b: Risk Alerts
    Detects high leverage, concentration, large losses, etc
    Real alerts would come from risk service - this is placeholder structure
    """
    # Placeholder implementation - real alerts would come from dedicated risk service
    alerts = []
    
    if client_id:
        # Check for concentration risk
        query = text("""
            SELECT 
                ch.client_id,
                ch.instrument_id,
                (ch.quantity * mq.mid_price) / ca.cash_balance as concentration_ratio
            FROM client_holdings ch
            JOIN market_quotes mq ON ch.instrument_id = mq.instrument_id
            JOIN client_accounts ca ON ch.account_id = ca.account_id
            WHERE ch.client_id = :client_id AND ca.cash_balance > 0
            HAVING concentration_ratio > 0.3
        """)
        results = db.execute(query, {"client_id": client_id}).fetchall()
        
        for row in results:
            alerts.append(RiskAlertResponse(
                alert_id=hash(f"concentration_{row[0]}_{row[1]}") % 2147483647,
                client_id=row[0],
                alert_type="Concentration",
                severity="High",
                message=f"Position exceeds 30% of account value: {float(row[2])*100:.1f}%",
                created_at=datetime.now()
            ))
    
    return alerts

def get_audit_trail(
    db: Session,
    entity_name: Optional[str] = None,
    entity_id: Optional[int] = None,
    limit: int = 100
) -> List[AuditLogResponse]:
    """
    ANALYTICS SECTION 10: Audit Trail & Compliance
    Complete audit history with state before/after
    """
    base_query = "SELECT * FROM audit_logs WHERE 1=1"
    params: Dict[str, Any] = {"limit": limit}
    
    if entity_name:
        base_query += " AND entity_name = :entity_name"
        params["entity_name"] = entity_name
    
    if entity_id:
        base_query += " AND entity_id = :entity_id"
        params["entity_id"] = entity_id
    
    base_query += " ORDER BY performed_at DESC LIMIT :limit"
    
    query = text(base_query)
    results = db.execute(query, params).fetchall()
    
    return [
        AuditLogResponse(
            audit_id=row[0],
            entity_name=row[1],
            entity_id=row[2],
            action_type=row[3],
            performed_by=row[4],
            performed_at=row[5],
            state_before=row[6],
            state_after=row[7]
        )
        for row in results
    ]

def get_order_lifecycle(db: Session, order_id: int) -> OrderLifecycleResponse:
    """
    ANALYTICS SECTION 10b: Order Lifecycle Traceability
    Complete order flow from submission to execution with audit trail
    """
    # Get order details
    order_query = text("""
        SELECT 
            o.order_id, o.client_id, fi.instrument_name, o.order_side,
            o.order_type, o.requested_quantity, o.order_status,
            o.created_at, o.updated_at
        FROM orders o
        JOIN financial_instruments fi ON o.instrument_id = fi.instrument_id
        WHERE o.order_id = :order_id
    """)
    order_result = db.execute(order_query, {"order_id": order_id}).fetchone()
    
    if not order_result:
        raise ValueError(f"Order {order_id} not found")
    
    # Get fills
    fills_query = text("""
        SELECT fill_id, executed_quantity, executed_price, executed_at
        FROM fills
        WHERE order_id = :order_id
        ORDER BY executed_at
    """)
    fills_result = db.execute(fills_query, {"order_id": order_id}).fetchall()
    
    fills = [
        FillResponse(
            fill_id=row[0],
            order_id=order_id,
            executed_quantity=row[1],
            executed_price=Decimal(row[2]),
            executed_at=row[3]
        )
        for row in fills_result
    ]
    
    # Get audit entries
    audit_entries = get_audit_trail(db, entity_name="orders", entity_id=order_id)
    
    filled_at = None
    if fills:
        filled_at = fills[-1].executed_at
    
    return OrderLifecycleResponse(
        order_id=order_id,
        client_id=order_result[1],
        instrument_name=order_result[2],
        order_side=order_result[3],
        order_type=order_result[4],
        requested_quantity=order_result[5],
        current_status=order_result[6],
        created_at=order_result[7],
        filled_at=filled_at,
        rejection_reason=None,
        fills=fills,
        audit_entries=audit_entries
    )

def get_platform_overview(db: Session) -> PlatformOverviewResponse:
    """
    ANALYTICS SECTION 8: Platform Overview
    Global statistics across all clients and trading activity
    """
    query = text("""
        SELECT 
            COUNT(DISTINCT cp.client_id) as total_clients,
            COUNT(DISTINCT ca.account_id) as total_accounts,
            COUNT(DISTINCT fi.instrument_id) as total_instruments,
            COUNT(DISTINCT o.order_id) as total_orders,
            COUNT(DISTINCT f.fill_id) as total_fills,
            SUM(ca.cash_balance) as total_cash,
            SUM(ch.quantity * mq.mid_price) as total_portfolio_value,
            AVG(CASE WHEN o.order_status = 'FILLED' THEN 1 ELSE 0 END) * 100 as avg_fill_rate
        FROM client_profiles cp
        LEFT JOIN client_accounts ca ON cp.client_id = ca.client_id
        LEFT JOIN financial_instruments fi ON 1=1
        LEFT JOIN orders o ON o.client_id = cp.client_id
        LEFT JOIN fills f ON o.order_id = f.order_id
        LEFT JOIN client_holdings ch ON ca.account_id = ch.account_id
        LEFT JOIN market_quotes mq ON ch.instrument_id = mq.instrument_id
    """)
    
    result = db.execute(query).fetchone()
    
    return PlatformOverviewResponse(
        total_clients=result[0] or 0,
        total_accounts=result[1] or 0,
        total_instruments=result[2] or 0,
        total_orders=result[3] or 0,
        total_fills=result[4] or 0,
        total_cash_managed=Decimal(result[5]) if result[5] else Decimal(0),
        total_portfolio_value=Decimal(result[6]) if result[6] else Decimal(0),
        avg_fill_rate_pct=float(result[7]) if result[7] else 0.0
    )
