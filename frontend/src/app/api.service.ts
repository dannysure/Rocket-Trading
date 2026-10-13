import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpInterceptorFn, HttpParams } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, catchError, map, throwError } from 'rxjs';

export const API_BASE_URL = 'http://localhost:8081/api/v1';

// ============================================================================
// API MODELS
// ============================================================================

export interface ApiResponse<T> {
  data: T;
  meta?: { requestId: string; timestamp: string };
}

export type Role = 'CLIENT' | 'ANALYST' | 'OPERATIONS';
export type OrderStatus = 'SUBMITTED' | 'ACCEPTED' | 'FILLED' | 'REJECTED' | 'CANCELLED';
export type Side = 'BUY' | 'SELL';
export type Segment = 'US' | 'UK' | 'IN' | 'FX' | 'CRYPTO';

export interface SessionResponse {
  clientId: number;
  sessionId: number;
  expiresAt: string;
  accessToken: string;
  tokenType: string;
  name: string;
  email: string;
  role: Role;
}

export interface RegisterRequest {
  name: string;
  email: string;
  password: string;
  riskProfile?: 'Cautious' | 'Balanced' | 'Adventurous';
}

export interface Instrument {
  instrumentId: number;
  symbol: string;
  name: string;
  assetClass: 'Equity' | 'FX' | 'Crypto' | string;
  baseCurrency: string;
  tradable: boolean;
  market: 'stock' | 'crypto' | 'fx';
  segment: Segment;
  exchange: string | null;
}

export interface Quote {
  symbol: string;
  bid: number;
  ask: number;
  price: number;
  capturedAt: string;
  market: string;
  delayed: boolean;
  currency: string | null;
  changePercent: number | null;
  previousClose: number | null;
  /** False while the market is closed: the price shown is its last one */
  current: boolean;
}

export interface Candle {
  date: string;
  open: number | null;
  high: number | null;
  low: number | null;
  close: number;
}

export interface IndicativePrice {
  symbol: string;
  name: string;
  side: Side;
  quantity: number;
  unitPrice: number;
  instrumentCurrency: string;
  fxRate: number;
  settlementCurrency: string;
  estimatedTotal: number;
  availableCash: number;
  heldQuantity: number;
  quoteCapturedAt: string;
  marketOpen: boolean;
}

export interface Position {
  symbol: string;
  name: string;
  assetClass: string;
  segment: Segment;
  instrumentCurrency: string;
  quantity: number;
  averageCost: number | null;
}

export interface PortfolioSummary {
  clientId: number;
  accountId: number;
  cashBalance: number;
  currency: string;
  netDeposits: number;
  positions: Position[];
}

export interface PositionValuation {
  symbol: string;
  name: string;
  assetClass: string;
  segment: Segment;
  quantity: number;
  averageCost: number | null;
  instrumentCurrency: string;
  price: number | null;
  fxRate: number | null;
  marketValue: number | null;
  unrealizedReturn: number | null;
  changePercent: number | null;
  priceAsOf: string | null;
  priced: boolean;
}

export interface PortfolioValuation {
  currency: string;
  cashBalance: number;
  holdingsValue: number;
  totalValue: number;
  netDeposits: number;
  totalReturn: number;
  dayChange: number;
  complete: boolean;
  valuedAt: string;
  positions: PositionValuation[];
}

export interface SubmitOrderRequest {
  symbol: string;
  side: Side;
  quantity: number;
  orderType: 'MARKET' | 'LIMIT';
  limitPrice?: number;
}

export interface Order {
  orderId: number;
  symbol: string;
  instrumentName: string | null;
  market: string;
  side: Side;
  orderType: 'MARKET' | 'LIMIT';
  quantity: number;
  limitPrice: number | null;
  status: OrderStatus;
  rejectionReason: string | null;
  submittedAt: string;
  awaitingMarket: boolean;
  instrumentCurrency: string | null;
  executedPrice: number | null;
  settlementAmount: number | null;
  settlementCurrency: string | null;
  executedAt: string | null;
}

export interface Fill {
  fillId: number;
  orderId: number;
  executedQuantity: number;
  executedPrice: number;
  instrumentCurrency: string;
  fxRate: number;
  settlementAmount: number;
  settlementCurrency: string;
  executedAt: string;
}

export interface AuditEvent {
  auditId: number;
  entityName: string;
  entityId: number;
  actionType: string;
  recordedAt: string;
  stateBefore: Record<string, any> | null;
  stateAfter: Record<string, any> | null;
}

export interface OrderTimeline {
  orderId: number;
  events: AuditEvent[];
}

export interface WatchlistItem {
  instrument: Instrument;
  addedAt: string;
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
  dataAsOf: string | null;
}

export interface InstrumentActivity {
  symbol: string;
  assetClass: string;
  segment: Segment;
  orderCount: number;
  fillCount: number;
  buyQuantity: number;
  sellQuantity: number;
  totalNotional: number;
  lastSubmittedAt: string;
}

export interface ClientSegmentActivity {
  riskProfile: string;
  clientCount: number;
  orderCount: number;
  totalNotional: number;
}

export interface MarketActivity {
  segment: Segment;
  orderCount: number;
  fillCount: number;
  totalNotional: number;
}

export interface VolumeBucket {
  periodStart: string;
  orderCount: number;
  fillCount: number;
  totalNotional: number;
}

export interface OpsOrderSummary {
  clientId: number;
  clientName: string;
  clientEmail: string;
  order: Order;
}

export interface LedgerEntry {
  transactionId: number;
  type: string;
  quantity: number | null;
  unitPrice: number | null;
  netAmount: number;
  recordedAt: string;
}

export interface OrderTrace {
  summary: OpsOrderSummary;
  fills: Fill[];
  ledger: LedgerEntry[];
  timeline: AuditEvent[];
}

/** The message a person should see for a failed request: the API's own message when it sent one. */
export function errorMessage(error: unknown, fallback = 'Something went wrong. Please try again.'): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) return 'Cannot reach Rocket Trading. Check your connection and try again.';
    const apiMessage = error.error?.error?.message;
    const details: { field: string; message: string }[] | undefined = error.error?.error?.details;
    if (details?.length) return details.map((d) => d.message).join('. ');
    if (apiMessage) return apiMessage;
  }
  return fallback;
}

// ============================================================================
// SESSION
// ============================================================================

export interface StoredSession {
  clientId: number;
  name: string;
  email: string;
  role: Role;
  token: string;
  expiresAt: string;
}

const SESSION_KEY = 'rocket-trading-session';

@Injectable({ providedIn: 'root' })
export class SessionStore {
  private readonly state = signal<StoredSession | null>(SessionStore.read());

  readonly session = this.state.asReadonly();
  readonly role = computed(() => this.state()?.role ?? null);
  readonly isStaff = computed(() => this.role() === 'ANALYST' || this.role() === 'OPERATIONS');

  /** True while a session exists and has not expired (the server is still the authority). */
  isActive(): boolean {
    const session = this.state();
    return session !== null && new Date(session.expiresAt).getTime() > Date.now();
  }

  set(session: StoredSession): void {
    this.state.set(session);
    try {
      sessionStorage.setItem(SESSION_KEY, JSON.stringify(session));
    } catch {
      // Storage unavailable (private mode, SSR): the session lives for this page only
    }
  }

  clear(): void {
    this.state.set(null);
    try {
      sessionStorage.removeItem(SESSION_KEY);
    } catch {
      // nothing stored
    }
  }

  /** Where a signed-in person starts, by role. */
  homeRoute(): string {
    switch (this.role()) {
      case 'ANALYST':
        return '/insights';
      case 'OPERATIONS':
        return '/ops';
      default:
        return '/dashboard';
    }
  }

  private static read(): StoredSession | null {
    try {
      if (typeof sessionStorage === 'undefined') return null;
      const stored = sessionStorage.getItem(SESSION_KEY);
      const parsed = stored ? (JSON.parse(stored) as StoredSession) : null;
      return parsed?.token && parsed.role ? parsed : null;
    } catch {
      return null;
    }
  }
}

/** Attaches the bearer token to API calls and ends the session when the server says it is no longer valid. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const sessions = inject(SessionStore);
  const router = inject(Router);
  const session = sessions.session();
  const isApi = req.url.startsWith(API_BASE_URL);
  const request = session && isApi
    ? req.clone({ setHeaders: { Authorization: `Bearer ${session.token}` } })
    : req;

  return next(request).pipe(
    catchError((error: HttpErrorResponse) => {
      const isSignIn = req.url.includes('/auth/sign-in') || req.url.includes('/auth/register');
      if (isApi && error.status === 401 && !isSignIn && session) {
        sessions.clear();
        router.navigate(['/login'], { queryParams: { expired: 1 } });
      }
      return throwError(() => error);
    }),
  );
};

// ============================================================================
// API CLIENTS
// ============================================================================

@Injectable({ providedIn: 'root' })
export class AuthApi {
  private readonly http = inject(HttpClient);
  private readonly sessions = inject(SessionStore);

  providers(): Observable<{ password: boolean; github: boolean }> {
    return this.http.get<ApiResponse<{ password: boolean; github: boolean }>>(`${API_BASE_URL}/auth/providers`).pipe(map((r) => r.data));
  }

  signIn(email: string, password: string): Observable<SessionResponse> {
    return this.http
      .post<ApiResponse<SessionResponse>>(`${API_BASE_URL}/auth/sign-in`, { email, password })
      .pipe(map((r) => this.remember(r.data)));
  }

  register(request: RegisterRequest): Observable<unknown> {
    return this.http.post(`${API_BASE_URL}/auth/register`, request);
  }

  /** Exchanges the one-time token from the GitHub redirect for this browser's session. */
  completeOAuth(redirectToken: string): Observable<StoredSession> {
    return this.http
      .post<ApiResponse<{ clientId: number; email: string; name: string; accessToken: string; expiresIn: number; role: Role }>>(
        `${API_BASE_URL}/auth/oauth/success`,
        {},
        { headers: { Authorization: `Bearer ${redirectToken}` } },
      )
      .pipe(
        map((r) => {
          const session: StoredSession = {
            clientId: r.data.clientId,
            name: r.data.name,
            email: r.data.email,
            role: r.data.role ?? 'CLIENT',
            token: r.data.accessToken,
            expiresAt: new Date(Date.now() + r.data.expiresIn * 1000).toISOString(),
          };
          this.sessions.set(session);
          return session;
        }),
      );
  }

  githubSignInUrl(): string {
    return `${API_BASE_URL}/oauth2/authorization/github`;
  }

  updateProfile(profile: { name: string; dateOfBirth: string; riskProfile: string }): Observable<unknown> {
    return this.http.put(`${API_BASE_URL}/auth/profile`, profile);
  }

  /** Revokes the session server-side; the local session is cleared whatever the outcome. */
  signOut(): Observable<unknown> {
    return this.http.post(`${API_BASE_URL}/auth/sign-out`, {}).pipe(
      map((r) => {
        this.sessions.clear();
        return r;
      }),
      catchError((error) => {
        this.sessions.clear();
        return throwError(() => error);
      }),
    );
  }

  private remember(response: SessionResponse): SessionResponse {
    this.sessions.set({
      clientId: response.clientId,
      name: response.name,
      email: response.email,
      role: response.role,
      token: response.accessToken,
      expiresAt: response.expiresAt,
    });
    return response;
  }
}

@Injectable({ providedIn: 'root' })
export class PortfolioApi {
  private readonly http = inject(HttpClient);

  summary(): Observable<PortfolioSummary> {
    return this.http.get<ApiResponse<PortfolioSummary>>(`${API_BASE_URL}/portfolio/summary`).pipe(map((r) => r.data));
  }

  valuation(): Observable<PortfolioValuation> {
    return this.http.get<ApiResponse<PortfolioValuation>>(`${API_BASE_URL}/portfolio/valuation`).pipe(map((r) => r.data));
  }
}

@Injectable({ providedIn: 'root' })
export class MarketApi {
  private readonly http = inject(HttpClient);

  instruments(): Observable<Instrument[]> {
    return this.http.get<ApiResponse<Instrument[]>>(`${API_BASE_URL}/instruments`).pipe(map((r) => r.data));
  }

  quote(symbol: string): Observable<Quote> {
    return this.http.get<ApiResponse<Quote>>(`${API_BASE_URL}/quotes/${encodeURIComponent(symbol)}`).pipe(map((r) => r.data));
  }

  history(symbol: string, days = 30): Observable<Candle[]> {
    return this.http
      .get<ApiResponse<Candle[]>>(`${API_BASE_URL}/quotes/${encodeURIComponent(symbol)}/history`, { params: { days } })
      .pipe(map((r) => r.data));
  }

  indicative(symbol: string, side: Side, quantity: number): Observable<IndicativePrice> {
    return this.http
      .get<ApiResponse<IndicativePrice>>(`${API_BASE_URL}/quotes/${encodeURIComponent(symbol)}/indicative`, {
        params: { side, quantity },
      })
      .pipe(map((r) => r.data));
  }
}

@Injectable({ providedIn: 'root' })
export class OrderApi {
  private readonly http = inject(HttpClient);

  /** The idempotency key makes a retried submission return the same order instead of placing a second. */
  submit(request: SubmitOrderRequest, idempotencyKey: string): Observable<Order> {
    return this.http
      .post<ApiResponse<Order>>(`${API_BASE_URL}/orders`, request, { headers: { 'Idempotency-Key': idempotencyKey } })
      .pipe(map((r) => r.data));
  }

  list(): Observable<Order[]> {
    return this.http.get<ApiResponse<Order[]>>(`${API_BASE_URL}/orders`).pipe(map((r) => r.data));
  }

  get(orderId: number): Observable<Order> {
    return this.http.get<ApiResponse<Order>>(`${API_BASE_URL}/orders/${orderId}`).pipe(map((r) => r.data));
  }

  fills(orderId: number): Observable<Fill[]> {
    return this.http.get<ApiResponse<Fill[]>>(`${API_BASE_URL}/fills/${orderId}`).pipe(map((r) => r.data));
  }

  timeline(orderId: number): Observable<OrderTimeline> {
    return this.http.get<ApiResponse<OrderTimeline>>(`${API_BASE_URL}/orders/${orderId}/timeline`).pipe(map((r) => r.data));
  }
}

@Injectable({ providedIn: 'root' })
export class WatchlistApi {
  private readonly http = inject(HttpClient);

  list(): Observable<WatchlistItem[]> {
    return this.http.get<ApiResponse<WatchlistItem[]>>(`${API_BASE_URL}/watchlist`).pipe(map((r) => r.data));
  }

  add(symbol: string): Observable<WatchlistItem[]> {
    return this.http
      .put<ApiResponse<WatchlistItem[]>>(`${API_BASE_URL}/watchlist/${encodeURIComponent(symbol)}`, {})
      .pipe(map((r) => r.data));
  }

  remove(symbol: string): Observable<WatchlistItem[]> {
    return this.http
      .delete<ApiResponse<WatchlistItem[]>>(`${API_BASE_URL}/watchlist/${encodeURIComponent(symbol)}`)
      .pipe(map((r) => r.data));
  }
}

@Injectable({ providedIn: 'root' })
export class ReportingApi {
  private readonly http = inject(HttpClient);

  overview(from: Date, to: Date): Observable<ReportingOverview> {
    return this.get<ReportingOverview>('overview', from, to);
  }

  instruments(from: Date, to: Date): Observable<InstrumentActivity[]> {
    return this.get<InstrumentActivity[]>('instruments', from, to);
  }

  clientSegments(from: Date, to: Date): Observable<ClientSegmentActivity[]> {
    return this.get<ClientSegmentActivity[]>('segments', from, to);
  }

  markets(from: Date, to: Date): Observable<MarketActivity[]> {
    return this.get<MarketActivity[]>('markets', from, to);
  }

  volume(from: Date, to: Date, bucket: 'day' | 'week'): Observable<VolumeBucket[]> {
    return this.get<VolumeBucket[]>('volume', from, to, { bucket });
  }

  private get<T>(path: string, from: Date, to: Date, extra: Record<string, string> = {}): Observable<T> {
    const params = new HttpParams({ fromObject: { from: from.toISOString(), to: to.toISOString(), ...extra } });
    return this.http.get<ApiResponse<T>>(`${API_BASE_URL}/reporting/${path}`, { params }).pipe(map((r) => r.data));
  }
}

@Injectable({ providedIn: 'root' })
export class OpsApi {
  private readonly http = inject(HttpClient);

  search(query: string, status: string): Observable<OpsOrderSummary[]> {
    const params: Record<string, string> = {};
    if (query.trim()) params['q'] = query.trim();
    if (status) params['status'] = status;
    return this.http.get<ApiResponse<OpsOrderSummary[]>>(`${API_BASE_URL}/ops/orders`, { params }).pipe(map((r) => r.data));
  }

  trace(orderId: number): Observable<OrderTrace> {
    return this.http.get<ApiResponse<OrderTrace>>(`${API_BASE_URL}/ops/orders/${orderId}`).pipe(map((r) => r.data));
  }
}
