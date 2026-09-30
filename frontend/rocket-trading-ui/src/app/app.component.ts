import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject, OnInit, OnDestroy } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { ApiService } from './api.service';
import { FillResponse, OrderResponse, PortfolioSummaryResponse, QuoteResponse, SessionResponse } from './models';

@Component({
  selector: 'app-root',
  imports: [CommonModule, ReactiveFormsModule, DatePipe, DecimalPipe],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent implements OnInit, OnDestroy {
  private readonly formBuilder = inject(FormBuilder);
  private readonly apiService = inject(ApiService);

  readonly registerForm = this.formBuilder.nonNullable.group({
    name: ['', [Validators.required]],
    email: ['', [Validators.required, Validators.email]],
    dateOfBirth: ['1990-01-01'],
    riskProfile: ['Balanced'],
    initialCash: [10000]
  });

  readonly signInForm = this.formBuilder.nonNullable.group({
    email: ['', [Validators.required, Validators.email]]
  });

  readonly quoteForm = this.formBuilder.nonNullable.group({
    symbol: ['AAPL', [Validators.required]],
    market: ['stock', [Validators.required]]
  });

  readonly orderForm = this.formBuilder.nonNullable.group({
    symbol: ['AAPL', [Validators.required]],
    side: ['BUY', [Validators.required]],
    quantity: [1, [Validators.required, Validators.min(0.000001)]],
    market: ['stock', [Validators.required]],
    orderType: ['MARKET', [Validators.required]],
    limitPrice: [null as number | null]
  });

  session: SessionResponse | null = this.loadSession();
  quote: QuoteResponse | null = null;
  portfolio: PortfolioSummaryResponse | null = null;
  orders: OrderResponse[] = [];
  infoMessage = 'Register a fixture client, sign in, then try quote and order flows.';
  errorMessage = '';

  fills: Record<number, FillResponse[]> = {};
  submitting = false;
  private pollTimer?: ReturnType<typeof setInterval>;
  private refreshInFlight = false;
  private refreshQueued = false;
  private sessionVersion = 0;

  ngOnInit(): void {
    if (this.session) void this.refreshDashboard();
  }

  ngOnDestroy(): void {
    this.sessionVersion++;
    this.stopPolling();
  }

  async register(): Promise<void> {
    if (this.registerForm.invalid) return;
    this.resetMessages();
    try {
      await firstValueFrom(this.apiService.register(this.registerForm.getRawValue()));
      this.infoMessage = 'Client registered. Sign in with the same email.';
    } catch (error) { this.handleError(error); }
  }

  async signIn(): Promise<void> {
    if (this.signInForm.invalid) return;
    this.resetMessages();
    try {
      const response = await firstValueFrom(this.apiService.signIn(this.signInForm.getRawValue()));
      this.clearSession();
      this.session = response.data;
      localStorage.setItem('rocketTradingToken', response.data.accessToken);
      localStorage.setItem('rocketTradingSession', JSON.stringify(response.data));
      this.infoMessage = 'Signed in.';
      await this.refreshDashboard();
    } catch (error) { this.handleError(error); }
  }

  async signOut(): Promise<void> {
    const token = this.session?.accessToken;
    this.clearSession();
    this.resetMessages();
    try {
      if (token) await firstValueFrom(this.apiService.signOut(token));
      this.infoMessage = 'Signed out.';
    } catch (error) { this.handleError(error); }
  }

  async fetchQuote(): Promise<void> {
    if (this.quoteForm.invalid || !this.session) return;
    const version = this.sessionVersion;
    this.resetMessages();
    try {
      const { symbol, market } = this.quoteForm.getRawValue();
      const response = await firstValueFrom(this.apiService.getQuote(symbol, market));
      if (version !== this.sessionVersion) return;
      this.quote = response.data;
      this.infoMessage = `Current quote for ${response.data.symbol}.`;
    } catch (error) { if (version === this.sessionVersion) this.handleError(error); }
  }

  async submitOrder(): Promise<void> {
    if (this.orderForm.invalid || !this.session || this.submitting) return;
    const request = this.orderForm.getRawValue();
    if (request.orderType === 'MARKET') request.limitPrice = null;
    if (request.orderType === 'LIMIT' && (!request.limitPrice || request.limitPrice <= 0)) {
      this.errorMessage = 'Enter a positive limit price.';
      return;
    }
    const version = this.sessionVersion;
    this.resetMessages();
    this.submitting = true;
    const fingerprint = JSON.stringify(request);
    let attempt: { fingerprint: string; key: string } | null = null;
    try { attempt = JSON.parse(localStorage.getItem('rocketTradingAttempt') ?? 'null'); } catch { /* Start a new request if storage is corrupt. */ }
    if (!attempt || attempt.fingerprint !== fingerprint) attempt = { fingerprint, key: crypto.randomUUID() };
    localStorage.setItem('rocketTradingAttempt', JSON.stringify(attempt));
    try {
      const response = await firstValueFrom(this.apiService.submitOrder(request, attempt.key));
      if (version !== this.sessionVersion) return;
      localStorage.removeItem('rocketTradingAttempt');
      this.infoMessage = `Order ${response.data.orderId} ${response.data.status.toLowerCase()}. Status updates automatically.`;
    } catch (error) {
      if (version !== this.sessionVersion) return;
      const status = (error as { status?: number }).status;
      if (status && status >= 400 && status < 500) localStorage.removeItem('rocketTradingAttempt');
      this.handleError(error);
      if (!status || status >= 500) this.errorMessage += ' Retry the same order to safely check its outcome.';
    } finally {
      if (version === this.sessionVersion) {
        this.submitting = false;
        await this.refreshDashboard();
      }
    }
  }

  async refreshDashboard(): Promise<void> {
    if (!this.session) return;
    if (this.refreshInFlight) { this.refreshQueued = true; return; }
    const version = this.sessionVersion;
    this.refreshInFlight = true;
    try {
      // Read the portfolio after the order state: a terminal status must never be
      // paired with an older balance from a racing parallel request.
      const orders = await firstValueFrom(this.apiService.listOrders());
      if (version !== this.sessionVersion) return;
      const portfolio = await firstValueFrom(this.apiService.getPortfolio());
      if (version !== this.sessionVersion) return;
      this.portfolio = portfolio.data;
      this.orders = orders.data;
      this.syncPolling();
      const missing = this.orders.filter(order => order.status === 'FILLED' && !this.fills[order.orderId]);
      await Promise.all(missing.map(async order => {
        const response = await firstValueFrom(this.apiService.listFills(order.orderId));
        if (version === this.sessionVersion) this.fills[order.orderId] = response.data;
      }));
    } catch (error) {
      if (version === this.sessionVersion) this.handleError(error);
    } finally {
      if (version === this.sessionVersion) {
        this.refreshInFlight = false;
        if (this.refreshQueued) { this.refreshQueued = false; void this.refreshDashboard(); }
      }
    }
  }

  private syncPolling(): void {
    if (this.orders.some(order => order.status === 'ACCEPTED' || order.status === 'SUBMITTED')) {
      this.pollTimer ??= setInterval(() => void this.refreshDashboard(), 2000);
    } else this.stopPolling();
  }

  private stopPolling(): void {
    if (this.pollTimer !== undefined) clearInterval(this.pollTimer);
    this.pollTimer = undefined;
  }

  private clearSession(): void {
    this.sessionVersion++;
    this.stopPolling();
    this.session = null;
    this.quote = null;
    this.portfolio = null;
    this.orders = [];
    this.fills = {};
    this.submitting = false;
    this.refreshInFlight = false;
    this.refreshQueued = false;
    localStorage.removeItem('rocketTradingToken');
    localStorage.removeItem('rocketTradingSession');
    localStorage.removeItem('rocketTradingAttempt');
  }

  private loadSession(): SessionResponse | null {
    try {
      const session = JSON.parse(localStorage.getItem('rocketTradingSession') ?? 'null') as SessionResponse | null;
      if (session?.accessToken && Date.parse(session.expiresAt) > Date.now()) return session;
    } catch { /* Expired or corrupt sessions must be signed in again. */ }
    localStorage.removeItem('rocketTradingToken');
    localStorage.removeItem('rocketTradingSession');
    return null;
  }

  private resetMessages(): void { this.errorMessage = ''; this.infoMessage = ''; }

  private handleError(error: unknown): void {
    const httpError = error as { status?: number; error?: { error?: { message?: string } }; message?: string };
    if (httpError.status === 401) this.clearSession();
    this.errorMessage = httpError.error?.error?.message ?? httpError.message ?? 'Request failed.';
  }
}
