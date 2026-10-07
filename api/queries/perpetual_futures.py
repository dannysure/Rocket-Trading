"""
DB-backed perpetual futures market queries and order execution.
"""
from __future__ import annotations

import json
import os
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from decimal import Decimal, ROUND_HALF_UP
from typing import Any, Dict, Optional
from urllib import error as urllib_error
from urllib import parse as urllib_parse
from urllib import request as urllib_request

from sqlalchemy import text
from sqlalchemy.orm import Session

from ..models import (
    DirectTradingAccountResponse,
    PerpetualMarketOverviewResponse,
    PerpetualOrderFillResponse,
    PerpetualOrderResponse,
    PerpetualOrderSubmissionResponse,
    PerpetualPositionResponse,
    SubmitPerpetualOrderRequest,
)

ZERO = Decimal("0")
ONE = Decimal("1")
PRICE_SCALE = Decimal("0.0001")
AMOUNT_SCALE = Decimal("0.000001")
USD_SCALE = Decimal("0.01")
BPS_SCALE = Decimal("0.0001")


@dataclass(frozen=True)
class LiveSpotQuote:
    symbol: str
    price: Decimal
    bid: Decimal
    ask: Decimal
    spread_bps: Decimal
    change_percent: float
    as_of: datetime


@dataclass(frozen=True)
class MarketState:
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


@dataclass(frozen=True)
class PositionState:
    signed_quantity: Decimal
    entry_price: Decimal
    leverage: int
    realized_pnl_usd: Decimal
    cumulative_funding_usd: Decimal
    updated_at: Optional[datetime]


@dataclass(frozen=True)
class PositionMutation:
    signed_quantity: Decimal
    entry_price: Decimal
    leverage: int
    realized_pnl_usd: Decimal
    cumulative_funding_usd: Decimal
    realized_delta_usd: Decimal


def get_available_accounts(db: Session) -> list[DirectTradingAccountResponse]:
    query = text(
        """
        SELECT
            ca.account_id,
            ca.client_id,
            cp.client_full_name,
            ca.account_type,
            ca.currency,
            ca.cash_balance
        FROM client_accounts ca
        JOIN client_profiles cp ON cp.client_id = ca.client_id
        WHERE ca.account_type = 'DIRECT_TRADING'
        ORDER BY cp.client_full_name, ca.account_id
        """
    )
    results = db.execute(query).fetchall()

    return [
        DirectTradingAccountResponse(
            account_id=row[0],
            client_id=row[1],
            client_name=row[2],
            account_type=row[3],
            currency=row[4],
            cash_balance=Decimal(row[5]),
        )
        for row in results
    ]


def get_market_overview(
    db: Session,
    market_symbol: str,
    client_id: int,
    account_id: int,
) -> PerpetualMarketOverviewResponse:
    market = _get_market(db, market_symbol)
    account = _get_direct_account(db, client_id, account_id)
    spot_quote = fetch_live_spot_quote(market["spot_symbol"])
    position = _get_position(db, account_id, market["market_id"])
    market_state = _build_market_state(db, market, spot_quote)
    position_response = _build_position_response(position, market, market_state)
    account_equity_usd, available_collateral_usd = _calculate_account_balances(
        Decimal(account["cash_balance"]),
        position_response,
    )

    return PerpetualMarketOverviewResponse(
        market_symbol=market["market_symbol"],
        display_name=market["display_name"],
        asset_symbol=market["asset_symbol"],
        quote_currency=market["quote_currency"],
        settlement_asset=market["settlement_asset"],
        spot_symbol=market["spot_symbol"],
        trading_view_symbol=market["trading_view_symbol"],
        index_price=market_state.index_price,
        mark_price=market_state.mark_price,
        best_bid=market_state.best_bid,
        best_ask=market_state.best_ask,
        basis_bps=market_state.basis_bps,
        premium_index=market_state.premium_index,
        funding_rate=market_state.funding_rate,
        annualized_funding_rate=market_state.annualized_funding_rate,
        funding_direction=market_state.funding_direction,
        next_funding_at=market_state.next_funding_at,
        open_interest_usd=market_state.open_interest_usd,
        long_open_interest_usd=market_state.long_open_interest_usd,
        short_open_interest_usd=market_state.short_open_interest_usd,
        long_short_ratio=market_state.long_short_ratio,
        volume_24h_usd=market_state.volume_24h_usd,
        insurance_fund_usd=Decimal(market["insurance_fund_usd"]),
        spot_change_24h_pct=spot_quote.change_percent,
        quote_timestamp=spot_quote.as_of,
        account_id=account_id,
        client_id=client_id,
        available_collateral_usd=available_collateral_usd,
        account_equity_usd=account_equity_usd,
        maintenance_margin_rate=Decimal(market["maintenance_margin_rate"]),
        max_leverage=int(market["max_leverage"]),
        maker_fee_rate=Decimal(market["maker_fee_rate"]),
        taker_fee_rate=Decimal(market["taker_fee_rate"]),
        position=position_response,
        recent_orders=_get_recent_orders(db, account_id, market["market_id"]),
    )


def submit_order(
    db: Session,
    request: SubmitPerpetualOrderRequest,
) -> PerpetualOrderSubmissionResponse:
    market = _get_market(db, request.market_symbol)
    account = _get_direct_account(db, request.client_id, request.account_id)
    _validate_order_request(request, market)

    position = _lock_position(db, request.account_id, market["market_id"])
    spot_quote = fetch_live_spot_quote(market["spot_symbol"])
    market_state = _build_market_state(db, market, spot_quote)

    execution_price = _resolve_execution_price(request, market_state)
    notional_usd = _quantize_usd(execution_price * request.quantity * Decimal(market["contract_size"]))
    fee_rate = Decimal(market["taker_fee_rate"])
    fee_usd = _quantize_usd(notional_usd * fee_rate)
    quantity_delta = request.quantity if request.side == "BUY" else -request.quantity

    mutation = _apply_fill_to_position(
        position,
        quantity_delta=quantity_delta,
        execution_price=execution_price,
        leverage=request.leverage,
        reduce_only=request.reduce_only,
    )
    new_position_response = _build_position_response(
        PositionState(
            signed_quantity=mutation.signed_quantity,
            entry_price=mutation.entry_price,
            leverage=mutation.leverage,
            realized_pnl_usd=mutation.realized_pnl_usd,
            cumulative_funding_usd=mutation.cumulative_funding_usd,
            updated_at=datetime.now(timezone.utc),
        ),
        market,
        market_state,
    )
    account_equity_usd, available_collateral_usd = _calculate_account_balances(
        Decimal(account["cash_balance"]),
        new_position_response,
    )
    rejection_reason = _validate_margin(
        available_collateral_usd,
        position,
        mutation,
        request,
    )

    order_row = _insert_order(
        db,
        request=request,
        market_id=market["market_id"],
        mark_price=market_state.mark_price,
        notional_usd=notional_usd,
        fee_usd=fee_usd,
        status="REJECTED" if rejection_reason else "FILLED",
        average_fill_price=execution_price if rejection_reason is None else None,
        rejection_reason=rejection_reason,
    )

    order_response = _hydrate_order_response(order_row, None)

    if rejection_reason is not None:
        db.commit()
        return PerpetualOrderSubmissionResponse(
            order=order_response,
            market=get_market_overview(db, request.market_symbol, request.client_id, request.account_id),
        )

    fill_row = _insert_fill(
        db,
        order_id=order_row["order_id"],
        quantity=request.quantity,
        price=execution_price,
        fee_usd=fee_usd,
        realized_pnl_usd=mutation.realized_delta_usd,
    )
    _upsert_position(
        db,
        client_id=request.client_id,
        account_id=request.account_id,
        market_id=market["market_id"],
        mutation=mutation,
    )
    db.commit()

    order_response = _hydrate_order_response(order_row, fill_row)

    return PerpetualOrderSubmissionResponse(
        order=order_response,
        market=get_market_overview(db, request.market_symbol, request.client_id, request.account_id),
    )


def fetch_live_spot_quote(symbol: str) -> LiveSpotQuote:
    quote_api_base_url = os.getenv(
        "ROCKET_TRADING_QUOTES_BASE_URL",
        "https://y4t9nq2bqf.execute-api.eu-west-2.amazonaws.com/v1/quotes",
    )
    api_key = (
        os.getenv("MARKET_DATA_API_KEY")
        or os.getenv("ROCKET_TRADING_QUOTES_API_KEY")
        or os.getenv("X_API_KEY")
    )
    if not api_key:
        raise ValueError("Missing market data API key for perpetual futures quote lookup")

    url = f"{quote_api_base_url}/{urllib_parse.quote(symbol)}"
    request = urllib_request.Request(url, headers={"X-Api-Key": api_key})

    try:
        with urllib_request.urlopen(request, timeout=10) as response:
            payload = json.loads(response.read().decode("utf-8"))
    except urllib_error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="ignore")
        raise ValueError(f"Quote provider rejected {symbol}: {detail or exc.reason}") from exc
    except urllib_error.URLError as exc:
        raise ValueError(f"Quote provider is unavailable for {symbol}: {exc.reason}") from exc

    data = payload["data"]
    return LiveSpotQuote(
        symbol=data["symbol"],
        price=Decimal(str(data["price"])),
        bid=Decimal(str(data["bid"])),
        ask=Decimal(str(data["ask"])),
        spread_bps=Decimal(str(data["spreadBps"])),
        change_percent=float(data["changePercent"]),
        as_of=datetime.fromisoformat(data["asOf"].replace("Z", "+00:00")),
    )


def _get_market(db: Session, market_symbol: str) -> Dict[str, Any]:
    query = text(
        """
        SELECT
            market_id,
            market_symbol,
            display_name,
            asset_symbol,
            quote_currency,
            settlement_asset,
            spot_symbol,
            trading_view_symbol,
            contract_size,
            annual_interest_rate,
            funding_interval_hours,
            funding_clamp_bps,
            maker_fee_rate,
            taker_fee_rate,
            maintenance_margin_rate,
            max_leverage,
            impact_notional_usd,
            insurance_fund_usd,
            base_basis_bps
        FROM perpetual_markets
        WHERE market_symbol = :market_symbol
          AND is_active = TRUE
        """
    )
    row = db.execute(query, {"market_symbol": market_symbol.upper()}).mappings().first()
    if row is None:
        raise ValueError(f"Perpetual market {market_symbol} is not configured")
    return dict(row)


def _get_direct_account(db: Session, client_id: int, account_id: int) -> Dict[str, Any]:
    query = text(
        """
        SELECT
            ca.account_id,
            ca.client_id,
            ca.cash_balance,
            cp.client_full_name
        FROM client_accounts ca
        JOIN client_profiles cp ON cp.client_id = ca.client_id
        WHERE ca.account_id = :account_id
          AND ca.client_id = :client_id
          AND ca.account_type = 'DIRECT_TRADING'
        """
    )
    row = db.execute(query, {"client_id": client_id, "account_id": account_id}).mappings().first()
    if row is None:
        raise ValueError("Direct trading account was not found for the selected client")
    return dict(row)


def _get_position(db: Session, account_id: int, market_id: int) -> PositionState:
    query = text(
        """
        SELECT signed_quantity, entry_price, leverage, realized_pnl_usd, cumulative_funding_usd, updated_at
        FROM perpetual_positions
        WHERE account_id = :account_id AND market_id = :market_id
        """
    )
    row = db.execute(query, {"account_id": account_id, "market_id": market_id}).fetchone()
    if row is None:
        return PositionState(ZERO, ZERO, 5, ZERO, ZERO, None)
    return PositionState(
        signed_quantity=Decimal(row[0]),
        entry_price=Decimal(row[1]),
        leverage=int(row[2]),
        realized_pnl_usd=Decimal(row[3]),
        cumulative_funding_usd=Decimal(row[4]),
        updated_at=row[5],
    )


def _lock_position(db: Session, account_id: int, market_id: int) -> PositionState:
    query = text(
        """
        SELECT signed_quantity, entry_price, leverage, realized_pnl_usd, cumulative_funding_usd, updated_at
        FROM perpetual_positions
        WHERE account_id = :account_id AND market_id = :market_id
        FOR UPDATE
        """
    )
    row = db.execute(query, {"account_id": account_id, "market_id": market_id}).fetchone()
    if row is None:
        return PositionState(ZERO, ZERO, 5, ZERO, ZERO, None)
    return PositionState(
        signed_quantity=Decimal(row[0]),
        entry_price=Decimal(row[1]),
        leverage=int(row[2]),
        realized_pnl_usd=Decimal(row[3]),
        cumulative_funding_usd=Decimal(row[4]),
        updated_at=row[5],
    )


def _build_market_state(db: Session, market: Dict[str, Any], spot_quote: LiveSpotQuote) -> MarketState:
    aggregate_query = text(
        """
        SELECT
            COALESCE(SUM(CASE WHEN signed_quantity > 0 THEN signed_quantity ELSE 0 END), 0) AS long_qty,
            COALESCE(SUM(CASE WHEN signed_quantity < 0 THEN ABS(signed_quantity) ELSE 0 END), 0) AS short_qty
        FROM perpetual_positions
        WHERE market_id = :market_id
        """
    )
    fill_query = text(
        """
        SELECT COALESCE(SUM(f.quantity * f.price), 0)
        FROM perpetual_fills f
        JOIN perpetual_orders o ON o.order_id = f.order_id
        WHERE o.market_id = :market_id
          AND f.created_at >= :window_start
        """
    )
    aggregate = db.execute(aggregate_query, {"market_id": market["market_id"]}).fetchone()
    long_qty = Decimal(aggregate[0])
    short_qty = Decimal(aggregate[1])
    contract_size = Decimal(market["contract_size"])
    long_open_interest_usd = _quantize_usd(long_qty * contract_size * spot_quote.price)
    short_open_interest_usd = _quantize_usd(short_qty * contract_size * spot_quote.price)
    open_interest_usd = long_open_interest_usd + short_open_interest_usd
    imbalance_ratio = ZERO
    if open_interest_usd > ZERO:
        imbalance_ratio = (long_open_interest_usd - short_open_interest_usd) / open_interest_usd

    basis_bps = _quantize_bps(
        Decimal(market["base_basis_bps"]) + clamp_decimal(imbalance_ratio * Decimal("45"), Decimal("-35"), Decimal("35"))
    )
    mark_price = _quantize_price(spot_quote.price * (ONE + (basis_bps / Decimal("10000"))))
    premium_index = _quantize_rate((mark_price - spot_quote.price) / spot_quote.price)
    intervals_per_year = Decimal(365 * 24) / Decimal(market["funding_interval_hours"])
    interest_per_interval = Decimal(market["annual_interest_rate"]) / intervals_per_year
    clamp_rate = Decimal(market["funding_clamp_bps"]) / Decimal("10000")
    bounded_spread = clamp_decimal(interest_per_interval - premium_index, -clamp_rate, clamp_rate)
    funding_rate = _quantize_rate(premium_index + bounded_spread)
    annualized_funding_rate = _quantize_rate(
        funding_rate * (Decimal(24) / Decimal(market["funding_interval_hours"])) * Decimal(365)
    )
    spread_bps = max(Decimal("0.75"), spot_quote.spread_bps * Decimal("1.35"))
    best_bid = _quantize_price(mark_price * (ONE - (spread_bps / Decimal("20000"))))
    best_ask = _quantize_price(mark_price * (ONE + (spread_bps / Decimal("20000"))))
    volume_24h = Decimal(
        db.execute(
            fill_query,
            {
                "market_id": market["market_id"],
                "window_start": datetime.now(timezone.utc) - timedelta(hours=24),
            },
        ).scalar_one()
    )
    next_funding_at = _next_funding_timestamp(int(market["funding_interval_hours"]))

    return MarketState(
        index_price=_quantize_price(spot_quote.price),
        mark_price=mark_price,
        best_bid=best_bid,
        best_ask=best_ask,
        basis_bps=basis_bps,
        premium_index=premium_index,
        funding_rate=funding_rate,
        annualized_funding_rate=annualized_funding_rate,
        funding_direction=(
            "longs-pay-shorts"
            if funding_rate > ZERO
            else "shorts-pay-longs"
            if funding_rate < ZERO
            else "neutral"
        ),
        next_funding_at=next_funding_at,
        open_interest_usd=open_interest_usd,
        long_open_interest_usd=long_open_interest_usd,
        short_open_interest_usd=short_open_interest_usd,
        long_short_ratio=float(long_open_interest_usd / short_open_interest_usd) if short_open_interest_usd > ZERO else 0.0,
        volume_24h_usd=_quantize_usd(volume_24h),
    )


def _build_position_response(
    position: PositionState,
    market: Dict[str, Any],
    market_state: MarketState,
) -> Optional[PerpetualPositionResponse]:
    if position.signed_quantity == ZERO:
        return None

    contract_size = Decimal(market["contract_size"])
    quantity_abs = abs(position.signed_quantity)
    notional_usd = _quantize_usd(quantity_abs * contract_size * market_state.mark_price)
    direction = ONE if position.signed_quantity > ZERO else Decimal("-1")
    unrealized_pnl_usd = _quantize_usd(
        quantity_abs * contract_size * (market_state.mark_price - position.entry_price) * direction
    )
    initial_margin_usd = _quantize_usd(notional_usd / Decimal(position.leverage))
    maintenance_margin_usd = _quantize_usd(
        notional_usd * Decimal(market["maintenance_margin_rate"])
    )
    liquidation_multiplier = (
        ONE - (ONE / Decimal(position.leverage)) + Decimal(market["maintenance_margin_rate"])
        if position.signed_quantity > ZERO
        else ONE + (ONE / Decimal(position.leverage)) - Decimal(market["maintenance_margin_rate"])
    )
    liquidation_price = _quantize_price(position.entry_price * liquidation_multiplier)

    return PerpetualPositionResponse(
        market_symbol=market["market_symbol"],
        side="LONG" if position.signed_quantity > ZERO else "SHORT",
        signed_quantity=position.signed_quantity.quantize(AMOUNT_SCALE),
        entry_price=_quantize_price(position.entry_price),
        mark_price=market_state.mark_price,
        notional_usd=notional_usd,
        leverage=position.leverage,
        liquidation_price=liquidation_price,
        unrealized_pnl_usd=unrealized_pnl_usd,
        realized_pnl_usd=_quantize_usd(position.realized_pnl_usd),
        cumulative_funding_usd=_quantize_usd(position.cumulative_funding_usd),
        initial_margin_usd=initial_margin_usd,
        maintenance_margin_usd=maintenance_margin_usd,
        updated_at=position.updated_at,
    )


def _calculate_account_balances(
    cash_balance: Decimal,
    position: Optional[PerpetualPositionResponse],
) -> tuple[Decimal, Decimal]:
    if position is None:
        return _quantize_usd(cash_balance), _quantize_usd(cash_balance)

    account_equity = _quantize_usd(
        cash_balance
        + position.realized_pnl_usd
        + position.cumulative_funding_usd
        + position.unrealized_pnl_usd
    )
    available_collateral = _quantize_usd(account_equity - position.initial_margin_usd)
    return account_equity, available_collateral


def _get_recent_orders(db: Session, account_id: int, market_id: int) -> list[PerpetualOrderResponse]:
    order_query = text(
        """
        SELECT
            order_id,
            market_id,
            order_side,
            order_type,
            reduce_only,
            quantity,
            leverage,
            limit_price,
            status,
            mark_price,
            average_fill_price,
            notional_usd,
            fee_usd,
            rejection_reason,
            created_at,
            filled_at
        FROM perpetual_orders
        WHERE account_id = :account_id AND market_id = :market_id
        ORDER BY created_at DESC
        LIMIT 12
        """
    )
    fill_query = text(
        """
        SELECT fill_id, order_id, quantity, price, fee_usd, realized_pnl_usd, created_at
        FROM perpetual_fills
        WHERE order_id = :order_id
        ORDER BY created_at DESC
        LIMIT 1
        """
    )
    orders = []
    for row in db.execute(order_query, {"account_id": account_id, "market_id": market_id}).mappings():
        fill_row = db.execute(fill_query, {"order_id": row["order_id"]}).mappings().first()
        orders.append(_hydrate_order_response(dict(row), dict(fill_row) if fill_row else None))
    return orders


def _resolve_execution_price(
    request: SubmitPerpetualOrderRequest,
    market_state: MarketState,
) -> Decimal:
    if request.order_type == "MARKET":
        return market_state.best_ask if request.side == "BUY" else market_state.best_bid

    if request.limit_price is None:
        raise ValueError("Limit orders require a limit price")

    if request.side == "BUY" and request.limit_price >= market_state.best_ask:
        return request.limit_price
    if request.side == "SELL" and request.limit_price <= market_state.best_bid:
        return request.limit_price

    raise ValueError("Limit price does not cross the current market")


def _apply_fill_to_position(
    position: PositionState,
    quantity_delta: Decimal,
    execution_price: Decimal,
    leverage: int,
    reduce_only: bool,
) -> PositionMutation:
    existing_quantity = position.signed_quantity

    if reduce_only:
        if existing_quantity == ZERO or (existing_quantity > ZERO and quantity_delta > ZERO) or (
            existing_quantity < ZERO and quantity_delta < ZERO
        ):
            raise ValueError("Reduce-only orders must decrease an existing position")
        if abs(quantity_delta) > abs(existing_quantity):
            raise ValueError("Reduce-only quantity exceeds the open position size")

    if existing_quantity == ZERO:
        return PositionMutation(
            signed_quantity=quantity_delta,
            entry_price=execution_price,
            leverage=leverage,
            realized_pnl_usd=position.realized_pnl_usd,
            cumulative_funding_usd=position.cumulative_funding_usd,
            realized_delta_usd=ZERO,
        )

    if (existing_quantity > ZERO and quantity_delta > ZERO) or (existing_quantity < ZERO and quantity_delta < ZERO):
        new_quantity = existing_quantity + quantity_delta
        new_entry = (
            (abs(existing_quantity) * position.entry_price + abs(quantity_delta) * execution_price)
            / abs(new_quantity)
        )
        return PositionMutation(
            signed_quantity=new_quantity.quantize(AMOUNT_SCALE),
            entry_price=_quantize_price(new_entry),
            leverage=leverage,
            realized_pnl_usd=position.realized_pnl_usd,
            cumulative_funding_usd=position.cumulative_funding_usd,
            realized_delta_usd=ZERO,
        )

    closed_quantity = min(abs(existing_quantity), abs(quantity_delta))
    direction = ONE if existing_quantity > ZERO else Decimal("-1")
    realized_delta = _quantize_usd(closed_quantity * (execution_price - position.entry_price) * direction)
    new_quantity = existing_quantity + quantity_delta

    if new_quantity == ZERO:
        new_entry = ZERO
    elif (existing_quantity > ZERO and new_quantity > ZERO) or (existing_quantity < ZERO and new_quantity < ZERO):
        new_entry = position.entry_price
    else:
        new_entry = execution_price

    return PositionMutation(
        signed_quantity=new_quantity.quantize(AMOUNT_SCALE),
        entry_price=_quantize_price(new_entry),
        leverage=leverage,
        realized_pnl_usd=_quantize_usd(position.realized_pnl_usd + realized_delta),
        cumulative_funding_usd=position.cumulative_funding_usd,
        realized_delta_usd=realized_delta,
    )


def _validate_order_request(request: SubmitPerpetualOrderRequest, market: Dict[str, Any]) -> None:
    if request.market_symbol.upper() != market["market_symbol"]:
        raise ValueError("Submitted market symbol does not match the configured perpetual market")
    if request.side not in {"BUY", "SELL"}:
        raise ValueError("side must be BUY or SELL")
    if request.order_type not in {"MARKET", "LIMIT"}:
        raise ValueError("order_type must be MARKET or LIMIT")
    if request.quantity <= ZERO:
        raise ValueError("quantity must be positive")
    if request.leverage < 1 or request.leverage > int(market["max_leverage"]):
        raise ValueError(f"leverage must be between 1 and {market['max_leverage']}")
    if request.order_type == "LIMIT" and request.limit_price is None:
        raise ValueError("limit_price is required for LIMIT orders")


def _validate_margin(
    available_collateral_usd: Decimal,
    old_position: PositionState,
    mutation: PositionMutation,
    request: SubmitPerpetualOrderRequest,
) -> Optional[str]:
    if available_collateral_usd >= ZERO:
        return None

    if request.reduce_only and abs(mutation.signed_quantity) <= abs(old_position.signed_quantity):
        return None

    return "Insufficient available collateral for the requested leverage and position size"


def _insert_order(
    db: Session,
    request: SubmitPerpetualOrderRequest,
    market_id: int,
    mark_price: Decimal,
    notional_usd: Decimal,
    fee_usd: Decimal,
    status: str,
    average_fill_price: Optional[Decimal],
    rejection_reason: Optional[str],
) -> Dict[str, Any]:
    query = text(
        """
        INSERT INTO perpetual_orders (
            client_id,
            account_id,
            market_id,
            order_side,
            order_type,
            reduce_only,
            quantity,
            leverage,
            limit_price,
            status,
            rejection_reason,
            mark_price,
            average_fill_price,
            notional_usd,
            fee_usd,
            filled_at
        )
        VALUES (
            :client_id,
            :account_id,
            :market_id,
            :order_side,
            :order_type,
            :reduce_only,
            :quantity,
            :leverage,
            :limit_price,
            :status,
            :rejection_reason,
            :mark_price,
            :average_fill_price,
            :notional_usd,
            :fee_usd,
            :filled_at
        )
        RETURNING
            order_id,
            market_id,
            order_side,
            order_type,
            reduce_only,
            quantity,
            leverage,
            limit_price,
            status,
            mark_price,
            average_fill_price,
            notional_usd,
            fee_usd,
            rejection_reason,
            created_at,
            filled_at
        """
    )
    row = db.execute(
        query,
        {
            "client_id": request.client_id,
            "account_id": request.account_id,
            "market_id": market_id,
            "order_side": request.side,
            "order_type": request.order_type,
            "reduce_only": request.reduce_only,
            "quantity": request.quantity,
            "leverage": request.leverage,
            "limit_price": request.limit_price,
            "status": status,
            "rejection_reason": rejection_reason,
            "mark_price": _quantize_price(mark_price),
            "average_fill_price": _quantize_price(average_fill_price) if average_fill_price is not None else None,
            "notional_usd": notional_usd,
            "fee_usd": fee_usd,
            "filled_at": datetime.now(timezone.utc) if status == "FILLED" else None,
        },
    ).mappings().one()
    return dict(row)


def _insert_fill(
    db: Session,
    order_id: int,
    quantity: Decimal,
    price: Decimal,
    fee_usd: Decimal,
    realized_pnl_usd: Decimal,
) -> Dict[str, Any]:
    query = text(
        """
        INSERT INTO perpetual_fills (
            order_id,
            quantity,
            price,
            fee_usd,
            realized_pnl_usd
        )
        VALUES (:order_id, :quantity, :price, :fee_usd, :realized_pnl_usd)
        RETURNING fill_id, order_id, quantity, price, fee_usd, realized_pnl_usd, created_at
        """
    )
    row = db.execute(
        query,
        {
            "order_id": order_id,
            "quantity": quantity,
            "price": _quantize_price(price),
            "fee_usd": fee_usd,
            "realized_pnl_usd": realized_pnl_usd,
        },
    ).mappings().one()
    return dict(row)


def _upsert_position(
    db: Session,
    client_id: int,
    account_id: int,
    market_id: int,
    mutation: PositionMutation,
) -> None:
    query = text(
        """
        INSERT INTO perpetual_positions (
            client_id,
            account_id,
            market_id,
            signed_quantity,
            entry_price,
            leverage,
            realized_pnl_usd,
            cumulative_funding_usd,
            updated_at
        )
        VALUES (
            :client_id,
            :account_id,
            :market_id,
            :signed_quantity,
            :entry_price,
            :leverage,
            :realized_pnl_usd,
            :cumulative_funding_usd,
            :updated_at
        )
        ON CONFLICT (account_id, market_id) DO UPDATE SET
            signed_quantity = EXCLUDED.signed_quantity,
            entry_price = EXCLUDED.entry_price,
            leverage = EXCLUDED.leverage,
            realized_pnl_usd = EXCLUDED.realized_pnl_usd,
            cumulative_funding_usd = EXCLUDED.cumulative_funding_usd,
            updated_at = EXCLUDED.updated_at
        """
    )
    db.execute(
        query,
        {
            "client_id": client_id,
            "account_id": account_id,
            "market_id": market_id,
            "signed_quantity": mutation.signed_quantity,
            "entry_price": mutation.entry_price,
            "leverage": mutation.leverage,
            "realized_pnl_usd": mutation.realized_pnl_usd,
            "cumulative_funding_usd": mutation.cumulative_funding_usd,
            "updated_at": datetime.now(timezone.utc),
        },
    )


def _hydrate_order_response(
    order_row: Dict[str, Any],
    fill_row: Optional[Dict[str, Any]],
) -> PerpetualOrderResponse:
    fill = None
    if fill_row is not None:
        fill = PerpetualOrderFillResponse(
            fill_id=fill_row["fill_id"],
            order_id=fill_row["order_id"],
            quantity=Decimal(fill_row["quantity"]),
            price=Decimal(fill_row["price"]),
            fee_usd=Decimal(fill_row["fee_usd"]),
            realized_pnl_usd=Decimal(fill_row["realized_pnl_usd"]),
            created_at=fill_row["created_at"],
        )

    return PerpetualOrderResponse(
        order_id=order_row["order_id"],
        market_symbol="ETH-PERP",
        side=order_row["order_side"],
        order_type=order_row["order_type"],
        reduce_only=bool(order_row["reduce_only"]),
        quantity=Decimal(order_row["quantity"]),
        leverage=int(order_row["leverage"]),
        limit_price=Decimal(order_row["limit_price"]) if order_row["limit_price"] is not None else None,
        status=order_row["status"],
        mark_price=Decimal(order_row["mark_price"]),
        average_fill_price=Decimal(order_row["average_fill_price"]) if order_row["average_fill_price"] is not None else None,
        notional_usd=Decimal(order_row["notional_usd"]),
        fee_usd=Decimal(order_row["fee_usd"]),
        rejection_reason=order_row["rejection_reason"],
        created_at=order_row["created_at"],
        filled_at=order_row["filled_at"],
        fill=fill,
    )


def clamp_decimal(value: Decimal, minimum: Decimal, maximum: Decimal) -> Decimal:
    return min(max(value, minimum), maximum)


def _quantize_price(value: Decimal) -> Decimal:
    return Decimal(value).quantize(PRICE_SCALE, rounding=ROUND_HALF_UP)


def _quantize_usd(value: Decimal) -> Decimal:
    return Decimal(value).quantize(USD_SCALE, rounding=ROUND_HALF_UP)


def _quantize_rate(value: Decimal) -> Decimal:
    return Decimal(value).quantize(Decimal("0.00000001"), rounding=ROUND_HALF_UP)


def _quantize_bps(value: Decimal) -> Decimal:
    return Decimal(value).quantize(BPS_SCALE, rounding=ROUND_HALF_UP)


def _next_funding_timestamp(interval_hours: int) -> datetime:
    now = datetime.now(timezone.utc).replace(minute=0, second=0, microsecond=0)
    next_hour = ((now.hour // interval_hours) + 1) * interval_hours
    if next_hour >= 24:
        return (now + timedelta(days=1)).replace(hour=next_hour - 24)
    return now.replace(hour=next_hour)
