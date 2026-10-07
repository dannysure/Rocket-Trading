import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { map } from 'rxjs';

export interface DirectTradingAccount {
  accountId: number;
  clientId: number;
  clientName: string;
  accountType: string;
  currency: string;
  cashBalance: number;
}

export interface PerpetualPosition {
  marketSymbol: string;
  side: 'LONG' | 'SHORT';
  signedQuantity: number;
  entryPrice: number;
  markPrice: number;
  notionalUsd: number;
  leverage: number;
  liquidationPrice: number;
  unrealizedPnlUsd: number;
  realizedPnlUsd: number;
  cumulativeFundingUsd: number;
  initialMarginUsd: number;
  maintenanceMarginUsd: number;
  updatedAt: string | null;
}

export interface PerpetualOrderFill {
  fillId: number;
  orderId: number;
  quantity: number;
  price: number;
  feeUsd: number;
  realizedPnlUsd: number;
  createdAt: string;
}

export interface PerpetualOrder {
  orderId: number;
  marketSymbol: string;
  side: 'BUY' | 'SELL';
  orderType: 'MARKET' | 'LIMIT';
  reduceOnly: boolean;
  quantity: number;
  leverage: number;
  limitPrice: number | null;
  status: 'FILLED' | 'REJECTED' | 'SUBMITTED';
  markPrice: number;
  averageFillPrice: number | null;
  notionalUsd: number;
  feeUsd: number;
  rejectionReason: string | null;
  createdAt: string;
  filledAt: string | null;
  fill: PerpetualOrderFill | null;
}

export interface PerpetualMarketOverview {
  marketSymbol: string;
  displayName: string;
  assetSymbol: string;
  quoteCurrency: string;
  settlementAsset: string;
  spotSymbol: string;
  tradingViewSymbol: string;
  indexPrice: number;
  markPrice: number;
  bestBid: number;
  bestAsk: number;
  basisBps: number;
  premiumIndex: number;
  fundingRate: number;
  annualizedFundingRate: number;
  fundingDirection: string;
  nextFundingAt: string;
  openInterestUsd: number;
  longOpenInterestUsd: number;
  shortOpenInterestUsd: number;
  longShortRatio: number;
  volume24hUsd: number;
  insuranceFundUsd: number;
  spotChange24hPct: number;
  quoteTimestamp: string;
  accountId: number;
  clientId: number;
  availableCollateralUsd: number;
  accountEquityUsd: number;
  maintenanceMarginRate: number;
  maxLeverage: number;
  makerFeeRate: number;
  takerFeeRate: number;
  position: PerpetualPosition | null;
  recentOrders: PerpetualOrder[];
}

export interface SubmitPerpetualOrderRequest {
  clientId: number;
  accountId: number;
  marketSymbol: string;
  side: 'BUY' | 'SELL';
  orderType: 'MARKET' | 'LIMIT';
  quantity: number;
  leverage: number;
  limitPrice: number | null;
  reduceOnly: boolean;
}

export interface PerpetualOrderSubmission {
  order: PerpetualOrder;
  market: PerpetualMarketOverview;
}

@Injectable({ providedIn: 'root' })
export class PerpetualFuturesService {
  private readonly http = inject(HttpClient);

  getAccounts() {
    return this.http
      .get<unknown[]>('/api/perpetual-futures/accounts')
      .pipe(map((accounts) => accounts.map((account) => this.mapAccount(account))));
  }

  getMarketOverview(marketSymbol: string, clientId: number, accountId: number) {
    const params = new HttpParams()
      .set('client_id', String(clientId))
      .set('account_id', String(accountId));

    return this.http
      .get<unknown>(`/api/perpetual-futures/markets/${encodeURIComponent(marketSymbol)}`, { params })
      .pipe(map((response) => this.mapMarket(response)));
  }

  submitOrder(request: SubmitPerpetualOrderRequest) {
    return this.http
      .post<unknown>('/api/perpetual-futures/orders', {
        client_id: request.clientId,
        account_id: request.accountId,
        market_symbol: request.marketSymbol,
        side: request.side,
        order_type: request.orderType,
        quantity: request.quantity,
        leverage: request.leverage,
        limit_price: request.limitPrice,
        reduce_only: request.reduceOnly,
      })
      .pipe(map((response) => this.mapSubmission(response)));
  }

  private mapAccount(value: unknown): DirectTradingAccount {
    const record = value as Record<string, unknown>;
    return {
      accountId: Number(record['account_id']),
      clientId: Number(record['client_id']),
      clientName: String(record['client_name']),
      accountType: String(record['account_type']),
      currency: String(record['currency']),
      cashBalance: Number(record['cash_balance']),
    };
  }

  private mapPosition(value: unknown): PerpetualPosition {
    const record = value as Record<string, unknown>;
    return {
      marketSymbol: String(record['market_symbol']),
      side: (record['side'] === 'SHORT' ? 'SHORT' : 'LONG'),
      signedQuantity: Number(record['signed_quantity']),
      entryPrice: Number(record['entry_price']),
      markPrice: Number(record['mark_price']),
      notionalUsd: Number(record['notional_usd']),
      leverage: Number(record['leverage']),
      liquidationPrice: Number(record['liquidation_price']),
      unrealizedPnlUsd: Number(record['unrealized_pnl_usd']),
      realizedPnlUsd: Number(record['realized_pnl_usd']),
      cumulativeFundingUsd: Number(record['cumulative_funding_usd']),
      initialMarginUsd: Number(record['initial_margin_usd']),
      maintenanceMarginUsd: Number(record['maintenance_margin_usd']),
      updatedAt: record['updated_at'] ? String(record['updated_at']) : null,
    };
  }

  private mapFill(value: unknown): PerpetualOrderFill {
    const record = value as Record<string, unknown>;
    return {
      fillId: Number(record['fill_id']),
      orderId: Number(record['order_id']),
      quantity: Number(record['quantity']),
      price: Number(record['price']),
      feeUsd: Number(record['fee_usd']),
      realizedPnlUsd: Number(record['realized_pnl_usd']),
      createdAt: String(record['created_at']),
    };
  }

  private mapOrder(value: unknown): PerpetualOrder {
    const record = value as Record<string, unknown>;
    return {
      orderId: Number(record['order_id']),
      marketSymbol: String(record['market_symbol']),
      side: record['side'] === 'SELL' ? 'SELL' : 'BUY',
      orderType: record['order_type'] === 'LIMIT' ? 'LIMIT' : 'MARKET',
      reduceOnly: Boolean(record['reduce_only']),
      quantity: Number(record['quantity']),
      leverage: Number(record['leverage']),
      limitPrice: record['limit_price'] === null || record['limit_price'] === undefined ? null : Number(record['limit_price']),
      status:
        record['status'] === 'REJECTED'
          ? 'REJECTED'
          : record['status'] === 'SUBMITTED'
            ? 'SUBMITTED'
            : 'FILLED',
      markPrice: Number(record['mark_price']),
      averageFillPrice:
        record['average_fill_price'] === null || record['average_fill_price'] === undefined
          ? null
          : Number(record['average_fill_price']),
      notionalUsd: Number(record['notional_usd']),
      feeUsd: Number(record['fee_usd']),
      rejectionReason: record['rejection_reason'] ? String(record['rejection_reason']) : null,
      createdAt: String(record['created_at']),
      filledAt: record['filled_at'] ? String(record['filled_at']) : null,
      fill: record['fill'] ? this.mapFill(record['fill']) : null,
    };
  }

  private mapMarket(value: unknown): PerpetualMarketOverview {
    const record = value as Record<string, unknown>;
    return {
      marketSymbol: String(record['market_symbol']),
      displayName: String(record['display_name']),
      assetSymbol: String(record['asset_symbol']),
      quoteCurrency: String(record['quote_currency']),
      settlementAsset: String(record['settlement_asset']),
      spotSymbol: String(record['spot_symbol']),
      tradingViewSymbol: String(record['trading_view_symbol']),
      indexPrice: Number(record['index_price']),
      markPrice: Number(record['mark_price']),
      bestBid: Number(record['best_bid']),
      bestAsk: Number(record['best_ask']),
      basisBps: Number(record['basis_bps']),
      premiumIndex: Number(record['premium_index']),
      fundingRate: Number(record['funding_rate']),
      annualizedFundingRate: Number(record['annualized_funding_rate']),
      fundingDirection: String(record['funding_direction']),
      nextFundingAt: String(record['next_funding_at']),
      openInterestUsd: Number(record['open_interest_usd']),
      longOpenInterestUsd: Number(record['long_open_interest_usd']),
      shortOpenInterestUsd: Number(record['short_open_interest_usd']),
      longShortRatio: Number(record['long_short_ratio']),
      volume24hUsd: Number(record['volume_24h_usd']),
      insuranceFundUsd: Number(record['insurance_fund_usd']),
      spotChange24hPct: Number(record['spot_change_24h_pct']),
      quoteTimestamp: String(record['quote_timestamp']),
      accountId: Number(record['account_id']),
      clientId: Number(record['client_id']),
      availableCollateralUsd: Number(record['available_collateral_usd']),
      accountEquityUsd: Number(record['account_equity_usd']),
      maintenanceMarginRate: Number(record['maintenance_margin_rate']),
      maxLeverage: Number(record['max_leverage']),
      makerFeeRate: Number(record['maker_fee_rate']),
      takerFeeRate: Number(record['taker_fee_rate']),
      position: record['position'] ? this.mapPosition(record['position']) : null,
      recentOrders: Array.isArray(record['recent_orders'])
        ? record['recent_orders'].map((order) => this.mapOrder(order))
        : [],
    };
  }

  private mapSubmission(value: unknown): PerpetualOrderSubmission {
    const record = value as Record<string, unknown>;
    return {
      order: this.mapOrder(record['order']),
      market: this.mapMarket(record['market']),
    };
  }
}
