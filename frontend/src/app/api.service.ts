import { Injectable, inject, signal, computed } from '@angular/core';
import { HttpClient, HttpHeaders, HttpErrorResponse } from '@angular/common/http';
import { HttpInterceptor, HttpRequest, HttpHandler, HttpEvent } from '@angular/common/http';
import { Observable, throwError, BehaviorSubject } from 'rxjs';
import { catchError, retry, tap } from 'rxjs/operators';

// ============================================================================
// API MODELS & INTERFACES
// ============================================================================

export interface ApiResponse<T> {
  success: boolean;
  data?: T;
  error?: string;
  meta?: {
    requestId: string;
    timestamp: string;
  };
}

export interface SessionResponse {
  clientId: number;
  sessionId: number;
  expiresAt: string;
  accessToken: string;
  tokenType: string;
}

export interface ClientRegistrationResponse {
  clientId: number;
  email: string;
  name: string;
  registeredAt: string;
}

export interface RegisterClientRequest {
  name: string;
  email: string;
  dateOfBirth: string;
  riskProfile: 'Cautious' | 'Balanced' | 'Adventurous';
}

export interface SignInRequest {
  email: string;
}

export interface Position {
  holdingId: number;
  symbol: string;
  instrumentId: number;
  quantity: number;
  averageCost: number;
  currentValue: number;
}

export interface PortfolioSummaryResponse {
  clientId: number;
  accountId: number;
  accountType: string;
  cashBalance: number;
  currency: string;
  positions: Position[];
  totalValue: number;
}

export interface SupportedInstrument {
  instrumentId: number;
  symbol: string;
  name: string;
  assetClass: 'Equity' | 'Bond' | 'Fund' | 'FX' | 'Crypto' | 'Cash';
  baseCurrency: string;
  isTradable: boolean;
}

export interface SubmitOrderRequest {
  symbol: string;
  side: 'BUY' | 'SELL';
  quantity: number;
  market?: 'stock' | 'crypto';
  orderType: 'MARKET' | 'LIMIT';
  limitPrice?: number;
}

export interface OrderResponse {
  orderId: number;
  clientId: number;
  instrumentId: number;
  symbol: string;
  side: string;
  quantity: number;
  orderType: string;
  status: 'SUBMITTED' | 'ACCEPTED' | 'FILLED' | 'REJECTED';
  submittedAt: string;
  rejectionReason?: string;
}

export interface FillResponse {
  fillId: number;
  orderId: number;
  executedQuantity: number;
  executedPrice: number;
  totalAmount: number;
  executedAt: string;
}

export interface OrderTimelineResponse {
  orderId: number;
  events: Array<{
    auditId: number;
    entityName: string;
    actionType: string;
    recordedAt: string;
    stateBefore: any;
    stateAfter: any;
  }>;
}

export interface QuoteResponse {
  symbol: string;
  bid: number;
  ask: number;
  price: number;
  market: string;
  capturedAt: string;
  // The provider's cache missed a refresh; the price is older than usual but within the accepted age
  delayed: boolean;
}

export interface ReportingOverviewResponse {
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

// ============================================================================
// HTTP AUTH INTERCEPTOR
// ============================================================================

@Injectable()
export class AuthInterceptor implements HttpInterceptor {
  private authService = inject(AuthService);

  intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    const session = this.authService.session();
    
    if (session && req.url.includes('/api/v1')) {
      // Clone request and add auth header
      req = req.clone({
        setHeaders: {
          Authorization: `Bearer ${session.token}`,
          'Content-Type': 'application/json',
        }
      });
    }

    return next.handle(req).pipe(
      retry(1),
      catchError((error: HttpErrorResponse) => {
        let errorMessage = 'An error occurred';
        
        if (error.error instanceof ErrorEvent) {
          errorMessage = `Error: ${error.error.message}`;
        } else {
          errorMessage = `Error Code: ${error.status}\nMessage: ${error.message}`;
          
          if (error.status === 401) {
            this.authService.clearSession();
          }
        }
        
        console.error(errorMessage);
        return throwError(() => new Error(errorMessage));
      })
    );
  }
}

// ============================================================================
// AUTH SERVICE
// ============================================================================

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = 'http://localhost:8081/api/v1';
  private readonly sessionStorageKey = 'rocket-trading-session';
  
  private sessionState = signal<StoredSession | null>(this.readSession());
  
  readonly session = computed(() => this.sessionState());
  readonly isSignedIn = computed(() => this.sessionState() !== null);
  readonly clientId = computed(() => this.sessionState()?.clientId ?? null);

  register(request: RegisterClientRequest): Observable<ApiResponse<ClientRegistrationResponse>> {
    return this.http.post<ApiResponse<ClientRegistrationResponse>>(
      `${this.apiUrl}/auth/register`,
      request
    );
  }

  signIn(email: string): Observable<ApiResponse<SessionResponse>> {
    return this.http.post<ApiResponse<SessionResponse>>(
      `${this.apiUrl}/auth/sign-in`,
      { email }
    ).pipe(
      tap((response) => {
        if (response.data) {
          this.writeSession({
            clientId: response.data.clientId,
            email,
            token: response.data.accessToken,
            expiresAt: response.data.expiresAt,
          });
        }
      }),
      catchError((error) => {
        console.error('Sign-in failed:', error);
        return throwError(() => error);
      })
    );
  }

  signOut(): Observable<any> {
    const session = this.sessionState();
    if (!session) {
      return throwError(() => new Error('No session to sign out'));
    }

    return this.http.post(
      `${this.apiUrl}/auth/sign-out`,
      {},
      { headers: this.authHeaders() }
    ).pipe(
      tap(() => this.clearSession()),
      catchError((error) => {
        this.clearSession();
        return throwError(() => error);
      })
    );
  }

  updateProfile(profileData: { name: string; dateOfBirth: string; riskProfile: string }): Observable<ApiResponse<any>> {
    return this.http.put<ApiResponse<any>>(
      `${this.apiUrl}/auth/profile`,
      profileData,
      { headers: this.authHeaders() }
    ).pipe(
      catchError((error) => {
        console.error('Profile update failed:', error);
        return throwError(() => error);
      })
    );
  }

  clearSession(): void {
    this.writeSession(null);
  }

  /** Re-read the session after another service (OAuthService) has written it to storage */
  reloadSession(): void {
    this.sessionState.set(this.readSession());
  }

  private readSession(): StoredSession | null {
    try {
      const stored = sessionStorage.getItem(this.sessionStorageKey);
      return stored ? JSON.parse(stored) : null;
    } catch {
      return null;
    }
  }

  private writeSession(session: StoredSession | null): void {
    if (session) {
      sessionStorage.setItem(this.sessionStorageKey, JSON.stringify(session));
    } else {
      sessionStorage.removeItem(this.sessionStorageKey);
    }
    this.sessionState.set(session);
  }

  protected authHeaders(): HttpHeaders {
    const session = this.sessionState();
    return new HttpHeaders({
      'Authorization': `Bearer ${session?.token || ''}`,
      'Content-Type': 'application/json',
    });
  }
}

// ============================================================================
// PORTFOLIO SERVICE
// ============================================================================

@Injectable({ providedIn: 'root' })
export class PortfolioService {
  private readonly http = inject(HttpClient);
  private readonly authService = inject(AuthService);
  private readonly apiUrl = 'http://localhost:8081/api/v1';

  getPortfolioSummary(): Observable<ApiResponse<PortfolioSummaryResponse>> {
    return this.http.get<ApiResponse<PortfolioSummaryResponse>>(
      `${this.apiUrl}/portfolio/summary`,
      { headers: this.authHeaders() }
    );
  }

  private authHeaders(): HttpHeaders {
    const session = this.authService.session();
    return new HttpHeaders({
      'Authorization': `Bearer ${session?.token || ''}`,
      'Content-Type': 'application/json',
    });
  }
}

// ============================================================================
// QUOTE SERVICE
// ============================================================================

@Injectable({ providedIn: 'root' })
export class QuoteService {
  private readonly http = inject(HttpClient);
  private readonly authService = inject(AuthService);
  private readonly apiUrl = 'http://localhost:8081/api/v1';
  // Not cached: prices move, and the backend already rejects stale quotes
  getQuote(symbol: string): Observable<ApiResponse<QuoteResponse>> {
    const normalized = symbol.trim().toUpperCase();
    // Fauxnance crypto symbols look like X:BTC-USD
    const market = normalized.startsWith('X:') ? 'crypto' : 'stock';
    return this.http.get<ApiResponse<QuoteResponse>>(
      `${this.apiUrl}/quotes/${encodeURIComponent(normalized)}`,
      { headers: this.authHeaders(), params: { market } }
    );
  }

  getSupportedInstruments(): Observable<ApiResponse<SupportedInstrument[]>> {
    return this.http.get<ApiResponse<SupportedInstrument[]>>(
      `${this.apiUrl}/instruments`,
      { headers: this.authHeaders() }
    );
  }

  private authHeaders(): HttpHeaders {
    const session = this.authService.session();
    return new HttpHeaders({
      'Authorization': `Bearer ${session?.token || ''}`,
      'Content-Type': 'application/json',
    });
  }
}

// ============================================================================
// ORDER SERVICE
// ============================================================================

@Injectable({ providedIn: 'root' })
export class OrderService {
  private readonly http = inject(HttpClient);
  private readonly authService = inject(AuthService);
  private readonly apiUrl = 'http://localhost:8081/api/v1';

  submitOrder(request: SubmitOrderRequest, idempotencyKey?: string): Observable<ApiResponse<OrderResponse>> {
    let headers = this.authHeaders();
    if (idempotencyKey) {
      headers = headers.set('Idempotency-Key', idempotencyKey);
    }

    return this.http.post<ApiResponse<OrderResponse>>(
      `${this.apiUrl}/orders`,
      request,
      { headers }
    );
  }

  listOrders(): Observable<ApiResponse<OrderResponse[]>> {
    return this.http.get<ApiResponse<OrderResponse[]>>(
      `${this.apiUrl}/orders`,
      { headers: this.authHeaders() }
    );
  }

  getOrder(orderId: number): Observable<ApiResponse<OrderResponse>> {
    return this.http.get<ApiResponse<OrderResponse>>(
      `${this.apiUrl}/orders/${orderId}`,
      { headers: this.authHeaders() }
    );
  }

  listFills(orderId: number): Observable<ApiResponse<FillResponse[]>> {
    return this.http.get<ApiResponse<FillResponse[]>>(
      `${this.apiUrl}/fills/${orderId}`,
      { headers: this.authHeaders() }
    );
  }

  getOrderTimeline(orderId: number): Observable<ApiResponse<OrderTimelineResponse>> {
    return this.http.get<ApiResponse<OrderTimelineResponse>>(
      `${this.apiUrl}/orders/${orderId}/timeline`,
      { headers: this.authHeaders() }
    );
  }

  private authHeaders(): HttpHeaders {
    const session = this.authService.session();
    return new HttpHeaders({
      'Authorization': `Bearer ${session?.token || ''}`,
      'Content-Type': 'application/json',
    });
  }
}

// ============================================================================
// REPORTING SERVICE
// ============================================================================

@Injectable({ providedIn: 'root' })
export class ReportingService {
  private readonly http = inject(HttpClient);
  private readonly authService = inject(AuthService);
  private readonly apiUrl = 'http://localhost:8081/api/v1';

  getReportingOverview(from: string, to: string): Observable<ApiResponse<ReportingOverviewResponse>> {
    return this.http.get<ApiResponse<ReportingOverviewResponse>>(
      `${this.apiUrl}/reporting/overview?from=${from}&to=${to}`,
      { headers: this.authHeaders() }
    );
  }

  getOrderMetrics(): Observable<ApiResponse<any>> {
    return this.http.get<ApiResponse<any>>(
      `${this.apiUrl}/reporting/orders`,
      { headers: this.authHeaders() }
    );
  }

  getClientMetrics(): Observable<ApiResponse<any>> {
    return this.http.get<ApiResponse<any>>(
      `${this.apiUrl}/reporting/clients`,
      { headers: this.authHeaders() }
    );
  }

  private authHeaders(): HttpHeaders {
    const session = this.authService.session();
    return new HttpHeaders({
      'Authorization': `Bearer ${session?.token || ''}`,
      'Content-Type': 'application/json',
    });
  }
}

// ============================================================================
// WEBSOCKET SERVICE (Real-time Updates)
// ============================================================================

@Injectable({ providedIn: 'root' })
export class WebSocketService {
  private authService = inject(AuthService);
  private wsUrl = 'ws://localhost:8081/ws';
  private socket: WebSocket | null = null;
  private messageSubject = new BehaviorSubject<any>(null);
  private connectionSubject = new BehaviorSubject<boolean>(false);

  public messages$ = this.messageSubject.asObservable();
  public connected$ = this.connectionSubject.asObservable();

  connect(): Observable<void> {
    return new Observable(observer => {
      if (this.socket && this.socket.readyState === WebSocket.OPEN) {
        observer.next();
        observer.complete();
        return;
      }

      this.socket = new WebSocket(this.wsUrl);

      this.socket.onopen = () => {
        this.connectionSubject.next(true);
        const clientId = this.authService.clientId();
        if (clientId) {
          this.subscribe(clientId);
        }
        observer.next();
        observer.complete();
      };

      this.socket.onmessage = (event) => {
        try {
          const message = JSON.parse(event.data);
          this.messageSubject.next(message);
        } catch (e) {
          console.error('Failed to parse WebSocket message:', e);
        }
      };

      this.socket.onerror = (error) => {
        this.connectionSubject.next(false);
        observer.error(error);
      };

      this.socket.onclose = () => {
        this.connectionSubject.next(false);
      };
    });
  }

  disconnect(): void {
    if (this.socket) {
      this.socket.close();
      this.socket = null;
      this.connectionSubject.next(false);
    }
  }

  subscribe(clientId: number): void {
    if (this.socket && this.socket.readyState === WebSocket.OPEN) {
      this.send({
        type: 'SUBSCRIBE',
        clientId: clientId,
      });
    }
  }

  private send(message: any): void {
    if (this.socket && this.socket.readyState === WebSocket.OPEN) {
      this.socket.send(JSON.stringify(message));
    }
  }
}
