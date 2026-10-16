import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { API_BASE_URL, ApiResponse, SessionStore } from './api.service';
import { Candle, OrderSide, PerpSide } from './perps.math';

export interface PerpMarket {
  symbol: string;
  baseAsset: string;
  underlyingSymbol: string;
  quoteCurrency: string;
  maxLeverage: number;
  takerFeeRate: number;
  maintenanceMarginRate: number;
  // Net long size that would put the mark 100% over the index; the premium is capped at maxPremium
  skewScale: number;
  maxPremium: number;
}

export interface PerpTicker {
  symbol: string;
  // Perp price: spot plus the premium the long/short open-interest imbalance sets
  markPrice: number;
  // Spot index (Coinbase mid)
  oraclePrice: number;
  bid: number;
  ask: number;
  // (mark - spot) / spot
  premium: number;
  // Charged on the hour: premium / 8, e.g. 0.000075 = 0.0075%; positive means longs pay shorts
  fundingRate: number;
  nextFundingAt: string;
  openInterest: number;
  longOpenInterest: number;
  shortOpenInterest: number;
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

  /** Every market's ticker, as the live stream pushes them; used to fill the page before live updates start. */
  tickers(): Observable<PerpTicker[]> {
    return this.http.get<ApiResponse<PerpTicker[]>>(`${this.base}/tickers`).pipe(map((r) => r.data));
  }

  /** Spot index candles, oldest first; intervalSeconds is 60, 300, 900 or 3600. */
  candles(symbol: string, intervalSeconds: number): Observable<Candle[]> {
    return this.http
      .get<ApiResponse<Candle[]>>(`${this.base}/markets/${encodeURIComponent(symbol)}/candles`, { params: { interval: intervalSeconds } })
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

/** An HTTP failure of the price stream; status 0 means the network failed or the stream ended. */
export class PerpStreamError extends Error {
  constructor(readonly status: number) {
    super(`Perps price stream failed (${status})`);
  }
}

/**
 * Live tickers for every market, pushed by the server several times a second. Uses fetch rather than
 * EventSource so the request can carry the bearer token.
 */
@Injectable({ providedIn: 'root' })
export class PerpsStream {
  private readonly sessions = inject(SessionStore);

  tickers(): Observable<PerpTicker[]> {
    return new Observable<PerpTicker[]>((subscriber) => {
      const controller = new AbortController();
      const token = this.sessions.session()?.token;
      fetch(`${API_BASE_URL}/perps/stream`, {
        headers: { Accept: 'text/event-stream', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
        cache: 'no-store',
        signal: controller.signal,
      })
        .then(async (response) => {
          if (!response.ok || !response.body) throw new PerpStreamError(response.status);
          const reader = response.body.pipeThrough(new TextDecoderStream()).getReader();
          const events = new ServerSentEvents((data) => subscriber.next(JSON.parse(data) as PerpTicker[]));
          for (;;) {
            const { value, done } = await reader.read();
            if (done) break;
            events.push(value);
          }
          // The server ends the stream after a while; the caller decides whether to reconnect
          subscriber.error(new PerpStreamError(0));
        })
        .catch((error) => {
          if (controller.signal.aborted) return;
          subscriber.error(error instanceof PerpStreamError ? error : new PerpStreamError(0));
        });
      return () => controller.abort();
    });
  }
}

/** Incremental parser for a text/event-stream body: calls onData with each event's data. */
export class ServerSentEvents {
  private buffer = '';

  constructor(private readonly onData: (data: string) => void) {}

  push(chunk: string): void {
    this.buffer += chunk.replace(/\r\n?/g, '\n');
    let end: number;
    while ((end = this.buffer.indexOf('\n\n')) >= 0) {
      const event = this.buffer.slice(0, end);
      this.buffer = this.buffer.slice(end + 2);
      const data = event.split('\n')
        .filter((line) => line.startsWith('data:'))
        .map((line) => line.slice(5).replace(/^ /, ''))
        .join('\n');
      if (data) this.onData(data);
    }
  }
}
