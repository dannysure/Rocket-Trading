import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';

const API_BASE_URL = 'http://localhost:8081/api/v1';
const SESSION_STORAGE_KEY = 'rocket-trading-session';

interface ApiEnvelope<T> {
  data: T;
  meta: {
    requestId: string;
    timestamp: string;
  };
}

interface SessionResponse {
  clientId: number;
  sessionId: number;
  expiresAt: string;
  accessToken: string;
  tokenType: string;
}

export interface ClientProfile {
  clientId: number;
  name: string;
  email: string;
}

export interface SupportedInstrument {
  instrumentId: number;
  symbol: string;
  name: string;
  assetClass: string;
  baseCurrency: string;
  tradable: boolean;
  market: 'stock' | 'crypto';
}

export interface Position {
  symbol: string;
  quantity: number;
}

export interface PortfolioSummary {
  clientId: number;
  accountId: number;
  cashBalance: number;
  currency: string;
  positions: Position[];
}

export interface OrderSummary {
  orderId: number;
  symbol: string;
  side: string;
  orderType: string;
  quantity: number;
  status: string;
  rejectionReason: string | null;
  submittedAt: string;
}

export interface FillSummary {
  fillId: number;
  orderId: number;
  executedQuantity: number;
  executedPrice: number;
  executedAt: string;
}

export interface OrderTimelineEvent {
  auditId: number;
  entityName: string;
  entityId: number;
  actionType: string;
  recordedAt: string;
  stateBefore: unknown;
  stateAfter: unknown;
}

export interface OrderTimeline {
  orderId: number;
  events: OrderTimelineEvent[];
}

export interface IndicativeQuote {
  symbol: string;
  bid: number;
  ask: number;
  price: number;
  bidVolume: number;
  askVolume: number;
  capturedAt: string;
  market: string;
}

export interface ReportingOverview {
  from: string;
  to: string;
  totalOrders: number;
  acceptedOrders: number;
  filledOrders: number;
  rejectedOrders: number;
  totalFills: number;
  totalNotional: number;
  activeClients: number;
}

type StoredSession = {
  clientId: number;
  email: string;
  token: string;
  expiresAt: string;
};

@Injectable({ providedIn: 'root' })
export class TradingApiService {
  private readonly http = inject(HttpClient);
  private readonly sessionState = signal<StoredSession | null>(this.readSession());

  readonly session = computed(() => this.sessionState());
  readonly isSignedIn = computed(() => this.sessionState() !== null);

  register(request: { name: string; email: string; initialCash: number }) {
    return this.http.post<ApiEnvelope<unknown>>(`${API_BASE_URL}/auth/register`, request);
  }

  signIn(email: string) {
    return this.http
      .post<ApiEnvelope<SessionResponse>>(`${API_BASE_URL}/auth/sign-in`, { email })
      .pipe(
        tap((response) => {
          this.writeSession({
            clientId: response.data.clientId,
            email,
            token: response.data.accessToken,
            expiresAt: response.data.expiresAt,
          });
        }),
      );
  }

  signOut() {
    return this.http.post<void>(`${API_BASE_URL}/auth/sign-out`, {}, { headers: this.authHeaders() });
  }

  clearSession() {
    this.writeSession(null);
  }

  getProfile(): Observable<ApiEnvelope<ClientProfile>> {
    return this.http.get<ApiEnvelope<ClientProfile>>(`${API_BASE_URL}/me`, {
      headers: this.authHeaders(),
    });
  }

  listSupportedInstruments(): Observable<ApiEnvelope<SupportedInstrument[]>> {
    return this.http.get<ApiEnvelope<SupportedInstrument[]>>(`${API_BASE_URL}/instruments`, {
      headers: this.authHeaders(),
    });
  }

  getPortfolioSummary(): Observable<ApiEnvelope<PortfolioSummary>> {
    return this.http.get<ApiEnvelope<PortfolioSummary>>(`${API_BASE_URL}/portfolio/summary`, {
      headers: this.authHeaders(),
    });
  }

  listOrders(): Observable<ApiEnvelope<OrderSummary[]>> {
    return this.http.get<ApiEnvelope<OrderSummary[]>>(`${API_BASE_URL}/orders`, {
      headers: this.authHeaders(),
    });
  }

  submitOrder(request: {
    symbol: string;
    side: string;
    quantity: number;
    market: string;
    orderType: string;
    limitPrice: number | null;
  }) {
    return this.http.post<ApiEnvelope<OrderSummary>>(`${API_BASE_URL}/orders`, request, {
      headers: this.authHeaders({
        'Idempotency-Key': crypto.randomUUID(),
      }),
    });
  }

  listFills(orderId: number): Observable<ApiEnvelope<FillSummary[]>> {
    return this.http.get<ApiEnvelope<FillSummary[]>>(`${API_BASE_URL}/fills/${orderId}`, {
      headers: this.authHeaders(),
    });
  }

  getOrderTimeline(orderId: number): Observable<ApiEnvelope<OrderTimeline>> {
    return this.http.get<ApiEnvelope<OrderTimeline>>(`${API_BASE_URL}/orders/${orderId}/timeline`, {
      headers: this.authHeaders(),
    });
  }

  getIndicativeQuote(symbol: string, market: string): Observable<ApiEnvelope<IndicativeQuote>> {
    return this.http.get<ApiEnvelope<IndicativeQuote>>(
      `${API_BASE_URL}/quotes/${encodeURIComponent(symbol)}?market=${encodeURIComponent(market)}`,
      { headers: this.authHeaders() },
    );
  }

  getReportingOverview(): Observable<ApiEnvelope<ReportingOverview>> {
    return this.http.get<ApiEnvelope<ReportingOverview>>(`${API_BASE_URL}/reporting/overview`, {
      headers: this.authHeaders(),
    });
  }

  private authHeaders(extraHeaders?: Record<string, string>): HttpHeaders {
    const session = this.sessionState();
    let headers = new HttpHeaders(extraHeaders ?? {});
    if (session) {
      headers = headers.set('Authorization', `Bearer ${session.token}`);
    }
    return headers;
  }

  private readSession(): StoredSession | null {
    if (typeof window === 'undefined') {
      return null;
    }

    const raw = window.localStorage.getItem(SESSION_STORAGE_KEY);
    if (!raw) {
      return null;
    }

    try {
      return JSON.parse(raw) as StoredSession;
    } catch {
      window.localStorage.removeItem(SESSION_STORAGE_KEY);
      return null;
    }
  }

  private writeSession(session: StoredSession | null) {
    this.sessionState.set(session);
    if (typeof window === 'undefined') {
      return;
    }

    if (session) {
      window.localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(session));
      return;
    }

    window.localStorage.removeItem(SESSION_STORAGE_KEY);
  }
}
