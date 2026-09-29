import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { map } from 'rxjs';

export interface MarketQuoteApiResponse {
  data: {
    symbol: string;
    price: number;
    bid: number;
    ask: number;
    spreadBps: number;
    currency: string;
    change: number;
    changePercent: number;
    previousClose: number;
    asOf: string;
    marketState: string;
  };
  meta: {
    asOf: string;
    disclaimer: string;
    symbol: string;
    source: string;
    stale: boolean;
    spreadSource: string;
  };
}

export interface MarketQuote {
  symbol: string;
  price: number;
  bid: number;
  ask: number;
  spreadBps: number;
  currency: string;
  change: number;
  changePercent: number;
  previousClose: number;
  asOf: string;
  marketState: string;
  disclaimer: string;
  source: string;
  stale: boolean;
  spreadSource: string;
}

@Injectable({ providedIn: 'root' })
export class MarketDataService {
  private readonly http = inject(HttpClient);

  getQuote(symbol: string) {
    return this.http
      .get<MarketQuoteApiResponse>(`/api/quotes/${encodeURIComponent(symbol)}`)
      .pipe(
        map((response): MarketQuote => ({
          ...response.data,
          disclaimer: response.meta.disclaimer,
          source: response.meta.source,
          stale: response.meta.stale,
          spreadSource: response.meta.spreadSource,
        })),
      );
  }
}
