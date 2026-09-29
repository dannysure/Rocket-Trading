import { CommonModule, CurrencyPipe, DatePipe, JsonPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit, computed, inject, isDevMode, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { forkJoin, interval, Subscription, switchMap } from 'rxjs';
import {
  ClientProfile,
  FillSummary,
  IndicativeQuote,
  OrderSummary,
  OrderTimeline,
  PortfolioSummary,
  ReportingOverview,
  SupportedInstrument,
  TradingApiService,
} from './trading-api.service';

@Component({
  selector: 'app-portfolio-page',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, CurrencyPipe, DatePipe, JsonPipe],
  template: `
    <main class="page trading-page">
      <section class="hero">
        <div class="hero-copy">
          <p class="section-label">Connected trading workflow</p>
          <h1>Run the Spring API, sign in, place an order, and watch it settle live.</h1>
          <p class="lead">
            This page is now wired to the Spring Boot backend on port 8081 and reads persistent
            state from PostgreSQL through the backend API.
          </p>
        </div>

        <aside class="summary-card">
          <p class="summary-kicker">Stack status</p>
          <ng-container *ngIf="isSignedIn(); else signedOutState">
            <h2>{{ profile()?.name ?? 'Signed in' }}</h2>
            <dl>
              <div>
                <dt>Email</dt>
                <dd>{{ profile()?.email ?? session()?.email }}</dd>
              </div>
              <div>
                <dt>Client ID</dt>
                <dd>{{ session()?.clientId }}</dd>
              </div>
              <div>
                <dt>Session expires</dt>
                <dd>{{ session()?.expiresAt | date : 'short' }}</dd>
              </div>
              <div>
                <dt>Database-backed cash</dt>
                <dd>
                  {{
                    (portfolio()?.cashBalance ?? 0)
                      | currency : (portfolio()?.currency ?? 'USD') : 'symbol' : '1.2-2'
                  }}
                </dd>
              </div>
            </dl>
            <div class="summary-actions">
              <button class="button button-secondary" type="button" (click)="refreshDashboard()">
                Refresh
              </button>
              <button class="button button-secondary" type="button" (click)="signOut()">
                Sign out
              </button>
            </div>
          </ng-container>

          <ng-template #signedOutState>
            <h2>Backend ready for sign-in</h2>
            <p class="summary-copy">
              Start PostgreSQL and Spring Boot first, then register a fixture client below.
            </p>
          </ng-template>
        </aside>
      </section>

      <section class="banner notice" *ngIf="notice()">{{ notice() }}</section>
      <section class="banner error" *ngIf="errorMessage()">{{ errorMessage() }}</section>

      <section class="auth-grid" *ngIf="!isSignedIn()">
        <article class="panel">
          <div class="panel-header">
            <div>
              <p class="panel-kicker">Step 1</p>
              <h2>Register a fixture client</h2>
            </div>
          </div>

          <form [formGroup]="registerForm" (ngSubmit)="register()" class="form-grid">
            <label>
              <span>Name</span>
              <input formControlName="name" placeholder="Joanna Trader" />
            </label>

            <label>
              <span>Email</span>
              <input formControlName="email" type="email" placeholder="joanna@example.com" />
            </label>

            <label>
              <span>Initial cash</span>
              <input formControlName="initialCash" type="number" min="0.01" step="0.01" />
            </label>

            <button class="button button-primary" type="submit" [disabled]="registerForm.invalid || busy()">
              Register
            </button>
          </form>
        </article>

        <article class="panel">
          <div class="panel-header">
            <div>
              <p class="panel-kicker">Step 2</p>
              <h2>Sign in and load live data</h2>
            </div>
          </div>

          <form [formGroup]="signInForm" (ngSubmit)="signIn()" class="form-grid">
            <label>
              <span>Email</span>
              <input formControlName="email" type="email" placeholder="joanna@example.com" />
            </label>

            <button class="button button-primary" type="submit" [disabled]="signInForm.invalid || busy()">
              Sign in
            </button>
          </form>
        </article>
      </section>

      <ng-container *ngIf="isSignedIn()">
        <section class="stats-grid">
          <article class="stat-card">
            <p>Cash balance</p>
            <strong>
              {{
                (portfolio()?.cashBalance ?? 0)
                  | currency : (portfolio()?.currency ?? 'USD') : 'symbol' : '1.2-2'
              }}
            </strong>
            <span>Persisted in PostgreSQL</span>
          </article>
          <article class="stat-card">
            <p>Positions</p>
            <strong>{{ portfolio()?.positions?.length ?? 0 }}</strong>
            <span>Read from /portfolio/summary</span>
          </article>
          <article class="stat-card">
            <p>Orders</p>
            <strong>{{ orders().length }}</strong>
            <span>Automatic refresh while pending</span>
          </article>
          <article class="stat-card" *ngIf="reportingOverview() as reporting">
            <p>Platform orders</p>
            <strong>{{ reporting.totalOrders }}</strong>
            <span>{{ reporting.activeClients }} active client(s) in reporting snapshot</span>
          </article>
        </section>

        <section class="dashboard-grid">
          <article class="panel">
            <div class="panel-header">
              <div>
                <p class="panel-kicker">Step 3</p>
                <h2>Trade ticket</h2>
              </div>
              <span>{{ instruments().length }} supported instruments</span>
            </div>

            <form [formGroup]="tradeForm" (ngSubmit)="submitOrder()" class="form-grid">
              <label>
                <span>Instrument</span>
                <select formControlName="symbol" (change)="syncSelectedInstrument()">
                  <option *ngFor="let instrument of instruments()" [value]="instrument.symbol">
                    {{ instrument.symbol }} · {{ instrument.name }}
                  </option>
                </select>
              </label>

              <label>
                <span>Side</span>
                <select formControlName="side">
                  <option value="BUY">BUY</option>
                  <option value="SELL">SELL</option>
                </select>
              </label>

              <label>
                <span>Quantity</span>
                <input formControlName="quantity" type="number" min="0.000001" step="0.000001" />
              </label>

              <label>
                <span>Order type</span>
                <select formControlName="orderType">
                  <option value="MARKET">MARKET</option>
                  <option value="LIMIT">LIMIT</option>
                </select>
              </label>

              <label>
                <span>Market</span>
                <input formControlName="market" readonly />
              </label>

              <label *ngIf="tradeForm.controls.orderType.value === 'LIMIT'">
                <span>Limit price</span>
                <input formControlName="limitPrice" type="number" min="0.0001" step="0.0001" />
              </label>

              <div class="form-actions">
                <button class="button button-secondary" type="button" (click)="refreshIndicativeQuote()">
                  Refresh indicative quote
                </button>
                <button class="button button-primary" type="submit" [disabled]="tradeForm.invalid || busy()">
                  Submit order
                </button>
              </div>
            </form>

            <div class="quote-card" *ngIf="indicativeQuote() as quote">
              <h3>Indicative quote</h3>
              <p>
                {{ quote.symbol }} · Bid
                {{ quote.bid | currency : 'USD' : 'symbol' : '1.2-2' }} · Ask
                {{ quote.ask | currency : 'USD' : 'symbol' : '1.2-2' }}
              </p>
              <span>Captured {{ quote.capturedAt | date : 'short' }}</span>
            </div>
          </article>

          <article class="panel">
            <div class="panel-header">
              <div>
                <p class="panel-kicker">API coverage</p>
                <h2>Supported catalogue</h2>
              </div>
            </div>

            <div class="instrument-list">
              <article class="instrument-pill" *ngFor="let instrument of instruments()">
                <strong>{{ instrument.symbol }}</strong>
                <span>{{ instrument.assetClass }} · {{ instrument.market }}</span>
              </article>
            </div>

            <div class="insight-card" *ngIf="reportingOverview() as reporting">
              <h3>Internal reporting snapshot</h3>
              <p>
                Filled orders: <strong>{{ reporting.filledOrders }}</strong> · Rejected orders:
                <strong>{{ reporting.rejectedOrders }}</strong> · Total notional:
                <strong>{{ reporting.totalNotional | currency : 'USD' : 'symbol' : '1.2-2' }}</strong>
              </p>
            </div>
          </article>
        </section>

        <section class="dashboard-grid">
          <article class="panel">
            <div class="panel-header">
              <div>
                <p class="panel-kicker">Portfolio</p>
                <h2>Positions and cash</h2>
              </div>
            </div>

            <div class="empty-state" *ngIf="(portfolio()?.positions?.length ?? 0) === 0">
              No positions yet. Submit a buy order and wait for it to fill.
            </div>

            <div class="table-wrap" *ngIf="(portfolio()?.positions?.length ?? 0) > 0">
              <table>
                <thead>
                  <tr>
                    <th>Symbol</th>
                    <th>Quantity</th>
                  </tr>
                </thead>
                <tbody>
                  <tr *ngFor="let position of portfolio()?.positions ?? []">
                    <td><span class="ticker-pill">{{ position.symbol }}</span></td>
                    <td>{{ position.quantity }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </article>

          <article class="panel">
            <div class="panel-header">
              <div>
                <p class="panel-kicker">Blotter</p>
                <h2>Orders and live status</h2>
              </div>
            </div>

            <div class="empty-state" *ngIf="orders().length === 0">
              No orders yet. Place an order to populate the blotter and audit trail.
            </div>

            <div class="table-wrap" *ngIf="orders().length > 0">
              <table>
                <thead>
                  <tr>
                    <th>Order</th>
                    <th>Symbol</th>
                    <th>Side</th>
                    <th>Quantity</th>
                    <th>Status</th>
                    <th>Submitted</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  <tr *ngFor="let order of orders()">
                    <td>#{{ order.orderId }}</td>
                    <td>{{ order.symbol }}</td>
                    <td>{{ order.side }}</td>
                    <td>{{ order.quantity }}</td>
                    <td>
                      <span class="status-pill" [class.is-rejected]="order.status === 'REJECTED'">
                        {{ order.status }}
                      </span>
                    </td>
                    <td>{{ order.submittedAt | date : 'short' }}</td>
                    <td>
                      <button class="inline-button" type="button" (click)="selectOrder(order.orderId)">
                        Details
                      </button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </article>
        </section>

        <section class="dashboard-grid" *ngIf="selectedOrder()">
          <article class="panel">
            <div class="panel-header">
              <div>
                <p class="panel-kicker">Selected order</p>
                <h2>Fills</h2>
              </div>
              <span>#{{ selectedOrder()?.orderId }}</span>
            </div>

            <div class="empty-state" *ngIf="fills().length === 0">
              No fills recorded yet for this order.
            </div>

            <ul class="detail-list" *ngIf="fills().length > 0">
              <li *ngFor="let fill of fills()">
                Fill {{ fill.fillId }} · {{ fill.executedQuantity }} @
                {{ fill.executedPrice | currency : 'USD' : 'symbol' : '1.2-2' }} ·
                {{ fill.executedAt | date : 'short' }}
              </li>
            </ul>
          </article>

          <article class="panel">
            <div class="panel-header">
              <div>
                <p class="panel-kicker">Audit trail</p>
                <h2>Order timeline</h2>
              </div>
            </div>

            <div class="empty-state" *ngIf="!(timeline()?.events?.length)">
              No audit events returned for this order yet.
            </div>

            <ul class="timeline" *ngIf="timeline()?.events?.length">
              <li *ngFor="let event of timeline()?.events ?? []">
                <div class="timeline-head">
                  <strong>{{ event.entityName }}</strong>
                  <span>{{ event.recordedAt | date : 'short' }}</span>
                </div>
                <p>{{ event.actionType }} · entity #{{ event.entityId }}</p>
                <details>
                  <summary>State after</summary>
                  <pre>{{ event.stateAfter | json }}</pre>
                </details>
              </li>
            </ul>
          </article>
        </section>
      </ng-container>
    </main>
  `,
  styles: [
    `
      .trading-page {
        width: min(1400px, 100%);
        text-align: left;
      }

      .hero,
      .auth-grid,
      .dashboard-grid,
      .stats-grid {
        display: grid;
        gap: 1.5rem;
      }

      .hero,
      .dashboard-grid {
        grid-template-columns: repeat(2, minmax(0, 1fr));
      }

      .auth-grid,
      .stats-grid {
        grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
      }

      .hero-copy,
      .summary-card,
      .panel,
      .stat-card,
      .banner {
        border: 1px solid rgba(59, 130, 246, 0.16);
        border-radius: 1rem;
        background: linear-gradient(135deg, rgba(25, 30, 45, 0.88), rgba(20, 25, 40, 0.88));
        box-shadow: 0 12px 28px rgba(0, 0, 0, 0.35);
      }

      .hero-copy,
      .summary-card,
      .panel,
      .stat-card,
      .banner {
        padding: 1.5rem;
      }

      .summary-kicker,
      .panel-kicker,
      .section-label {
        margin: 0 0 0.75rem;
        color: #60a5fa;
        text-transform: uppercase;
        letter-spacing: 0.15rem;
        font-size: 0.75rem;
        font-weight: 800;
      }

      h1,
      h2,
      h3,
      p {
        margin-top: 0;
      }

      .lead,
      .summary-copy,
      .insight-card p,
      .timeline p,
      .detail-list,
      label span {
        color: var(--text-secondary);
      }

      .summary-card dl {
        display: grid;
        gap: 0.75rem;
        margin: 0;
      }

      .summary-card dt {
        color: var(--text-secondary);
        font-size: 0.85rem;
      }

      .summary-card dd {
        margin: 0.25rem 0 0;
        font-weight: 700;
      }

      .summary-actions,
      .form-actions {
        display: flex;
        flex-wrap: wrap;
        gap: 0.75rem;
        margin-top: 1rem;
      }

      .banner {
        margin-top: 1.5rem;
      }

      .banner.notice {
        border-color: rgba(34, 197, 94, 0.35);
      }

      .banner.error {
        border-color: rgba(239, 68, 68, 0.35);
      }

      .form-grid {
        display: grid;
        gap: 1rem;
      }

      label {
        display: grid;
        gap: 0.4rem;
      }

      input,
      select {
        width: 100%;
        padding: 0.85rem 0.9rem;
        border: 1px solid rgba(255, 255, 255, 0.12);
        border-radius: 0.75rem;
        background: rgba(9, 9, 11, 0.7);
        color: var(--text-primary);
      }

      .instrument-list {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(170px, 1fr));
        gap: 0.75rem;
      }

      .instrument-pill,
      .insight-card,
      .quote-card {
        padding: 1rem;
        border-radius: 0.85rem;
        border: 1px solid rgba(255, 255, 255, 0.08);
        background: rgba(9, 9, 11, 0.45);
      }

      .instrument-pill {
        display: grid;
        gap: 0.25rem;
      }

      .empty-state {
        padding: 1rem 0;
        color: var(--text-secondary);
      }

      .table-wrap {
        overflow-x: auto;
      }

      table {
        width: 100%;
        border-collapse: collapse;
      }

      th,
      td {
        padding: 0.9rem 0.75rem;
        border-bottom: 1px solid rgba(255, 255, 255, 0.06);
        text-align: left;
      }

      th {
        color: var(--text-secondary);
        font-size: 0.85rem;
        text-transform: uppercase;
        letter-spacing: 0.05rem;
      }

      .ticker-pill,
      .status-pill {
        display: inline-flex;
        align-items: center;
        padding: 0.25rem 0.65rem;
        border-radius: 999px;
        background: rgba(59, 130, 246, 0.15);
      }

      .status-pill.is-rejected {
        background: rgba(239, 68, 68, 0.16);
      }

      .inline-button {
        color: #60a5fa;
        background: transparent;
        border: none;
        cursor: pointer;
      }

      .timeline,
      .detail-list {
        margin: 0;
        padding-left: 1.2rem;
      }

      .timeline li,
      .detail-list li {
        margin-bottom: 0.9rem;
      }

      .timeline-head {
        display: flex;
        justify-content: space-between;
        gap: 1rem;
      }

      pre {
        overflow-x: auto;
        padding: 0.75rem;
        border-radius: 0.75rem;
        background: rgba(9, 9, 11, 0.7);
        color: #dbeafe;
      }

      @media (max-width: 980px) {
        .hero,
        .dashboard-grid {
          grid-template-columns: 1fr;
        }
      }
    `,
  ],
})
export class PortfolioPageComponent implements OnInit, OnDestroy {
  private readonly tradingApi = inject(TradingApiService);
  private readonly formBuilder = inject(FormBuilder);

  readonly session = this.tradingApi.session;
  readonly isSignedIn = this.tradingApi.isSignedIn;
  readonly profile = signal<ClientProfile | null>(null);
  readonly instruments = signal<SupportedInstrument[]>([]);
  readonly portfolio = signal<PortfolioSummary | null>(null);
  readonly orders = signal<OrderSummary[]>([]);
  readonly fills = signal<FillSummary[]>([]);
  readonly timeline = signal<OrderTimeline | null>(null);
  readonly indicativeQuote = signal<IndicativeQuote | null>(null);
  readonly reportingOverview = signal<ReportingOverview | null>(null);
  readonly selectedOrderId = signal<number | null>(null);
  readonly notice = signal<string | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly busy = signal(false);
  readonly selectedOrder = computed(() =>
    this.orders().find((order) => order.orderId === this.selectedOrderId()) ?? null,
  );

  readonly registerForm = this.formBuilder.nonNullable.group({
    name: ['', [Validators.required]],
    email: ['', [Validators.required, Validators.email]],
    initialCash: [10000, [Validators.required, Validators.min(0.01)]],
  });

  readonly signInForm = this.formBuilder.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
  });

  readonly tradeForm = this.formBuilder.nonNullable.group({
    symbol: ['AAPL', [Validators.required]],
    side: ['BUY', [Validators.required]],
    quantity: [1, [Validators.required, Validators.min(0.000001)]],
    market: ['stock', [Validators.required]],
    orderType: ['MARKET', [Validators.required]],
    limitPrice: [''],
  });

  private pollSubscription?: Subscription;

  ngOnInit() {
    if (this.session()) {
      this.signInForm.patchValue({ email: this.session()?.email ?? '' });
      this.loadDashboard();
    }
  }

  ngOnDestroy() {
    this.stopPolling();
  }

  register() {
    if (this.registerForm.invalid) {
      return;
    }

    this.setBusy();
    const { name, email, initialCash } = this.registerForm.getRawValue();
    this.tradingApi.register({ name, email, initialCash }).subscribe({
      next: () => {
        this.signInForm.patchValue({ email });
        this.notice.set('Registration succeeded. Sign in next to open the trading dashboard.');
        this.errorMessage.set(null);
        this.clearBusy();
      },
      error: (error) => this.failRequest(error),
    });
  }

  signIn() {
    if (this.signInForm.invalid) {
      return;
    }

    this.setBusy();
    const { email } = this.signInForm.getRawValue();
    this.tradingApi.signIn(email).subscribe({
      next: () => {
        this.notice.set('Signed in. Loading live portfolio, orders, and reporting data.');
        this.errorMessage.set(null);
        this.clearBusy();
        this.loadDashboard();
      },
      error: (error) => this.failRequest(error),
    });
  }

  signOut() {
    this.tradingApi.signOut().subscribe({
      next: () => this.resetSession('Signed out.'),
      error: () => this.resetSession('Local session cleared after sign-out attempt.'),
    });
  }

  refreshDashboard() {
    this.loadDashboard();
  }

  syncSelectedInstrument() {
    const selected = this.instruments().find(
      (instrument) => instrument.symbol === this.tradeForm.controls.symbol.value,
    );
    if (!selected) {
      return;
    }

    this.tradeForm.patchValue({ market: selected.market });
  }

  refreshIndicativeQuote() {
    const { symbol, market } = this.tradeForm.getRawValue();
    this.tradingApi.getIndicativeQuote(symbol, market).subscribe({
      next: (response) => {
        this.indicativeQuote.set(response.data);
        this.errorMessage.set(null);
      },
      error: (error) => this.failRequest(error),
    });
  }

  submitOrder() {
    if (this.tradeForm.invalid) {
      return;
    }

    const limitPriceText = this.tradeForm.controls.limitPrice.value.trim();
    const request = {
      symbol: this.tradeForm.controls.symbol.value,
      side: this.tradeForm.controls.side.value,
      quantity: this.tradeForm.controls.quantity.value,
      market: this.tradeForm.controls.market.value,
      orderType: this.tradeForm.controls.orderType.value,
      limitPrice:
        this.tradeForm.controls.orderType.value === 'LIMIT' && limitPriceText
          ? Number(limitPriceText)
          : null,
    };

    if (request.orderType === 'LIMIT' && request.limitPrice === null) {
      this.errorMessage.set('Limit orders require a limit price.');
      return;
    }

    this.setBusy();
    this.tradingApi.submitOrder(request).subscribe({
      next: (response) => {
        this.notice.set(`Order #${response.data.orderId} accepted. Polling for status updates.`);
        this.errorMessage.set(null);
        this.clearBusy();
        this.selectedOrderId.set(response.data.orderId);
        this.loadDashboard();
        this.selectOrder(response.data.orderId);
      },
      error: (error) => this.failRequest(error),
    });
  }

  selectOrder(orderId: number) {
    this.selectedOrderId.set(orderId);
    forkJoin({
      fills: this.tradingApi.listFills(orderId),
      timeline: this.tradingApi.getOrderTimeline(orderId),
    }).subscribe({
      next: ({ fills, timeline }) => {
        this.fills.set(fills.data);
        this.timeline.set(timeline.data);
        this.errorMessage.set(null);
      },
      error: (error) => this.failRequest(error),
    });
  }

  private loadDashboard() {
    forkJoin({
      profile: this.tradingApi.getProfile(),
      instruments: this.tradingApi.listSupportedInstruments(),
      portfolio: this.tradingApi.getPortfolioSummary(),
      orders: this.tradingApi.listOrders(),
      reporting: this.tradingApi.getReportingOverview(),
    }).subscribe({
      next: ({ profile, instruments, portfolio, orders, reporting }) => {
        this.profile.set(profile.data);
        this.instruments.set(instruments.data);
        this.portfolio.set(portfolio.data);
        this.orders.set(orders.data);
        this.reportingOverview.set(reporting.data);
        this.errorMessage.set(null);
        this.syncSelectedInstrument();
        this.updatePolling(orders.data);

        const currentSelection = this.selectedOrderId();
        if (currentSelection !== null) {
          const stillVisible = orders.data.find((order) => order.orderId === currentSelection);
          if (stillVisible) {
            this.selectOrder(currentSelection);
          }
        }
      },
      error: (error) => this.failRequest(error),
    });
  }

  private updatePolling(orders: OrderSummary[]) {
    const hasPendingOrders = orders.some(
      (order) => order.status === 'ACCEPTED' || order.status === 'SUBMITTED',
    );

    if (!hasPendingOrders || typeof window === 'undefined') {
      this.stopPolling();
      return;
    }

    if (this.pollSubscription) {
      return;
    }

    this.pollSubscription = interval(2000)
      .pipe(
        switchMap(() =>
          forkJoin({
            portfolio: this.tradingApi.getPortfolioSummary(),
            orders: this.tradingApi.listOrders(),
            reporting: this.tradingApi.getReportingOverview(),
          }),
        ),
      )
      .subscribe({
        next: ({ portfolio, orders, reporting }) => {
          this.portfolio.set(portfolio.data);
          this.orders.set(orders.data);
          this.reportingOverview.set(reporting.data);
          if (this.selectedOrderId() !== null) {
            this.selectOrder(this.selectedOrderId()!);
          }
          if (!orders.data.some((order) => order.status === 'ACCEPTED' || order.status === 'SUBMITTED')) {
            this.stopPolling();
          }
        },
        error: (error) => this.failRequest(error),
      });
  }

  private stopPolling() {
    this.pollSubscription?.unsubscribe();
    this.pollSubscription = undefined;
  }

  private resetSession(message: string) {
    this.tradingApi.clearSession();
    this.stopPolling();
    this.profile.set(null);
    this.instruments.set([]);
    this.portfolio.set(null);
    this.orders.set([]);
    this.fills.set([]);
    this.timeline.set(null);
    this.indicativeQuote.set(null);
    this.reportingOverview.set(null);
    this.selectedOrderId.set(null);
    this.notice.set(message);
    this.errorMessage.set(null);
  }

  private failRequest(error: unknown) {
    this.clearBusy();
    this.errorMessage.set(this.describeError(error));
    if (isDevMode()) {
      console.error(error);
    }
  }

  private describeError(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      const apiMessage =
        typeof error.error === 'object' && error.error?.error?.message
          ? error.error.error.message
          : null;
      return apiMessage ?? error.message;
    }

    if (error instanceof Error) {
      return error.message;
    }

    return 'The request could not be completed.';
  }

  private setBusy() {
    this.busy.set(true);
    this.notice.set(null);
    this.errorMessage.set(null);
  }

  private clearBusy() {
    this.busy.set(false);
  }
}
