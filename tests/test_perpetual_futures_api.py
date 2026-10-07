from datetime import datetime, timezone
from decimal import Decimal

import pytest
from fastapi.testclient import TestClient

from api.database import get_db
from api.main import app
from api.models import (
    DirectTradingAccountResponse,
    PerpetualMarketOverviewResponse,
    PerpetualOrderResponse,
    PerpetualOrderSubmissionResponse,
)
from api.routes import perpetual_futures as perpetual_futures_routes


class DummySession:
    def rollback(self):
        self.rolled_back = True


@pytest.fixture
def client():
    session = DummySession()
    app.dependency_overrides[get_db] = lambda: session
    with TestClient(app) as test_client:
        yield test_client
    app.dependency_overrides.clear()


def build_market() -> PerpetualMarketOverviewResponse:
    return PerpetualMarketOverviewResponse(
        market_symbol="ETH-PERP",
        display_name="Ethereum Perpetual Market",
        asset_symbol="ETH",
        quote_currency="USD",
        settlement_asset="USDC",
        spot_symbol="ETH-USD",
        trading_view_symbol="BITSTAMP:ETHUSD",
        index_price=Decimal("3520.00"),
        mark_price=Decimal("3524.00"),
        best_bid=Decimal("3523.50"),
        best_ask=Decimal("3524.50"),
        basis_bps=Decimal("11.0"),
        premium_index=Decimal("0.00110000"),
        funding_rate=Decimal("0.00100000"),
        annualized_funding_rate=Decimal("0.11000000"),
        funding_direction="longs-pay-shorts",
        next_funding_at=datetime.now(timezone.utc),
        open_interest_usd=Decimal("480000000.00"),
        long_open_interest_usd=Decimal("251000000.00"),
        short_open_interest_usd=Decimal("229000000.00"),
        long_short_ratio=1.10,
        volume_24h_usd=Decimal("1950000000.00"),
        insurance_fund_usd=Decimal("38500000.00"),
        spot_change_24h_pct=1.8,
        quote_timestamp=datetime.now(timezone.utc),
        account_id=7,
        client_id=14,
        available_collateral_usd=Decimal("122500.00"),
        account_equity_usd=Decimal("126200.00"),
        maintenance_margin_rate=Decimal("0.005"),
        max_leverage=50,
        maker_fee_rate=Decimal("0.0002"),
        taker_fee_rate=Decimal("0.00055"),
        position=None,
        recent_orders=[],
    )


def build_order() -> PerpetualOrderResponse:
    now = datetime.now(timezone.utc)
    return PerpetualOrderResponse(
        order_id=99,
        market_symbol="ETH-PERP",
        side="BUY",
        order_type="MARKET",
        reduce_only=False,
        quantity=Decimal("0.500000"),
        leverage=5,
        limit_price=None,
        status="FILLED",
        mark_price=Decimal("3524.00"),
        average_fill_price=Decimal("3524.50"),
        notional_usd=Decimal("1762.25"),
        fee_usd=Decimal("0.97"),
        rejection_reason=None,
        created_at=now,
        filled_at=now,
        fill=None,
    )


def test_get_accounts_returns_direct_trading_accounts(client, monkeypatch):
    monkeypatch.setattr(
        perpetual_futures_routes.perpetual_futures,
        "get_available_accounts",
        lambda db: [
            DirectTradingAccountResponse(
                account_id=7,
                client_id=14,
                client_name="Ada Lovelace",
                account_type="DIRECT_TRADING",
                currency="USD",
                cash_balance=Decimal("125000.00"),
            )
        ],
    )

    response = client.get("/api/perpetual-futures/accounts")

    assert response.status_code == 200
    assert response.json()[0]["client_name"] == "Ada Lovelace"


def test_get_market_returns_eth_perp_overview(client, monkeypatch):
    monkeypatch.setattr(
        perpetual_futures_routes.perpetual_futures,
        "get_market_overview",
        lambda db, market_symbol, client_id, account_id: build_market(),
    )

    response = client.get("/api/perpetual-futures/markets/ETH-PERP?client_id=14&account_id=7")

    assert response.status_code == 200
    payload = response.json()
    assert payload["market_symbol"] == "ETH-PERP"
    assert payload["display_name"] == "Ethereum Perpetual Market"


def test_post_order_returns_submission_and_market_refresh(client, monkeypatch):
    monkeypatch.setattr(
        perpetual_futures_routes.perpetual_futures,
        "submit_order",
        lambda db, request: PerpetualOrderSubmissionResponse(
            order=build_order(),
            market=build_market(),
        ),
    )

    response = client.post(
        "/api/perpetual-futures/orders",
        json={
            "client_id": 14,
            "account_id": 7,
            "market_symbol": "ETH-PERP",
            "side": "BUY",
            "order_type": "MARKET",
            "quantity": 0.5,
            "leverage": 5,
            "limit_price": None,
            "reduce_only": False,
        },
    )

    assert response.status_code == 200
    payload = response.json()
    assert payload["order"]["status"] == "FILLED"
    assert payload["market"]["account_id"] == 7


def test_post_order_translates_business_validation_to_http_400(client, monkeypatch):
    def raise_validation_error(db, request):
        raise ValueError("Insufficient available collateral")

    monkeypatch.setattr(
        perpetual_futures_routes.perpetual_futures,
        "submit_order",
        raise_validation_error,
    )

    response = client.post(
        "/api/perpetual-futures/orders",
        json={
            "client_id": 14,
            "account_id": 7,
            "market_symbol": "ETH-PERP",
            "side": "BUY",
            "order_type": "MARKET",
            "quantity": 5,
            "leverage": 50,
            "limit_price": None,
            "reduce_only": False,
        },
    )

    assert response.status_code == 400
    assert response.json()["detail"] == "Insufficient available collateral"
