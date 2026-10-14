import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { API_BASE_URL, ApiResponse } from './api.service';
import { OrderSide, PerpSide } from './perps.math';

export interface PerpMarket {
  symbol: string;
  baseAsset: string;
  underlyingSymbol: string;
  quoteCurrency: string;
  maxLeverage: number;
  takerFeeRate: number;
  maintenanceMarginRate: number;
}

export interface PerpTicker {
  symbol: string;
  markPrice: number;
  oraclePrice: number;
  bid: number;
  ask: number;
  // Hourly rate, e.g. 0.0000125 = 0.00125% per hour; positive means longs pay shorts
  fundingRate: number;
  nextFundingAt: string;
  openInterest: number;
  maxLeverage: number;
  takerFeeRate: number;
  maintenanceMarginRate: number;
  capturedAt: string;
  delayed: boolean;
}

export interface PerpPosition {
  positionId: number;
  symbol: string;
  side: PerpSide;
  size: number;
  entryPrice: number;
  // Mark-dependent fields are null while the market's price is unavailable
  markPrice: number | null;
  positionValue: number | null;
  unrealizedPnl: number | null;
  returnOnEquity: number | null;
  liquidationPrice: number | null;
  margin: number;
  leverage: number;
  // Positive when the position has paid funding overall
  cumulativeFunding: number;
  openedAt: string;
}

export interface PerpAccount {
  accountId: number;
  currency: string;
  availableCollateral: number;
  totalMargin: number;
  unrealizedPnl: number;
  accountValue: number;
  positions: PerpPosition[];
}

export interface PerpOrderRequest {
  symbol: string;
  side: OrderSide;
  size: number;
  leverage: number;
  reduceOnly: boolean;
}

export interface PerpOrder {
  orderId: number;
  symbol: string;
  side: OrderSide;
  kind: 'MARKET' | 'LIQUIDATION';
  requestedSize: number;
  filledSize: number | null;
  leverage: number;
  reduceOnly: boolean;
  status: 'FILLED' | 'REJECTED';
  fillPrice: number | null;
  fee: number;
  realizedPnl: number;
  rejectionReason?: string | null;
  submittedAt: string;
}

export interface PerpFunding {
  fundingId: number;
  symbol: string;
  side: PerpSide;
  size: number;
  markPrice: number;
  fundingRate: number;
  // Positive when the position paid, negative when it received
  payment: number;
  fundingTime: string;
}

/** Perpetual futures endpoints; the auth interceptor adds the bearer token. */
@Injectable({ providedIn: 'root' })
export class PerpsApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_BASE_URL}/perps`;

  markets(): Observable<PerpMarket[]> {
    return this.http.get<ApiResponse<PerpMarket[]>>(`${this.base}/markets`).pipe(map((r) => r.data));
  }

  ticker(symbol: string): Observable<PerpTicker> {
    return this.http
      .get<ApiResponse<PerpTicker>>(`${this.base}/markets/${encodeURIComponent(symbol)}/ticker`)
      .pipe(map((r) => r.data));
  }

  account(): Observable<PerpAccount> {
    return this.http.get<ApiResponse<PerpAccount>>(`${this.base}/account`).pipe(map((r) => r.data));
  }

  submit(request: PerpOrderRequest, idempotencyKey: string): Observable<PerpOrder> {
    return this.http
      .post<ApiResponse<PerpOrder>>(`${this.base}/orders`, request, { headers: { 'Idempotency-Key': idempotencyKey } })
      .pipe(map((r) => r.data));
  }

  orders(): Observable<PerpOrder[]> {
    return this.http.get<ApiResponse<PerpOrder[]>>(`${this.base}/orders`).pipe(map((r) => r.data));
  }

  funding(): Observable<PerpFunding[]> {
    return this.http.get<ApiResponse<PerpFunding[]>>(`${this.base}/funding`).pipe(map((r) => r.data));
  }
}
