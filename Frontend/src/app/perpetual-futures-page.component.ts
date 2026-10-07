import { CommonModule, CurrencyPipe, DatePipe, DecimalPipe, PercentPipe, isPlatformBrowser } from '@angular/common';
import { Component, PLATFORM_ID, computed, effect, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { catchError, map, of, startWith, switchMap } from 'rxjs';
import {
  DirectTradingAccount,
  PerpetualFuturesService,
  PerpetualMarketOverview,
  PerpetualOrderSubmission,
} from './perpetual-futures.service';
import { TradingviewChartComponent } from './tradingview-chart.component';

interface AccountsState {
  readonly accounts: DirectTradingAccount[];
  readonly loading: boolean;
  readonly errorMessage: string | null;
}

interface OverviewState {
  readonly overview: PerpetualMarketOverview | null;
  readonly loading: boolean;
  readonly errorMessage: string | null;
}

interface BannerState {
  readonly tone: 'success' | 'error' | 'info';
  readonly message: string;
}

@Component({
  selector: 'app-perpetual-futures-page',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    TradingviewChartComponent,
    CurrencyPipe,
    DatePipe,
    DecimalPipe,
    PercentPipe,
  ],
  template: `
    <main class="page perp-shell">
      <section class="market-topbar">
        <div class="market-title">
          <p class="section-label">Perpetual futures</p>
          <h1>ETH-PERP</h1>
          <span>{{ overviewState().overview?.displayName ?? 'Ethereum Perpetual Market' }}</span>
        </div>

        <ng-container *ngIf="overviewState().overview as overview">
          <div class="stat-chip">
            <label>Mark</label>
            <strong>{{ overview.markPrice | currency : 'USD' : 'symbol' : '1.2-2' }}</strong>
            <span [class.positive]="overview.spotChange24hPct >= 0" [class.negative]="overview.spotChange24hPct < 0">
              {{ overview.spotChange24hPct / 100 | percent : '1.2-2' }}
            </span>
          </div>
          <div class="stat-chip">
            <label>Funding</label>
            <strong>{{ overview.fundingRate | percent : '1.4-4' }}</strong>
            <span>{{ overview.fundingDirection }}</span>
          </div>
          <div class="stat-chip">
            <label>Open interest</label>
            <strong>{{ overview.openInterestUsd | currency : 'USD' : 'symbol' : '1.0-0' }}</strong>
            <span>{{ overview.longShortRatio | number : '1.2-2' }} L/S</span>
          </div>
          <div class="stat-chip">
            <label>24h volume</label>
            <strong>{{ overview.volume24hUsd | currency : 'USD' : 'symbol' : '1.0-0' }}</strong>
            <span>Funding {{ overview.nextFundingAt | date : 'shortTime' }} UTC</span>
          </div>
        </ng-container>
      </section>

      <section class="trade-layout">
        <aside class="panel sidebar">
          <div class="panel-section">
            <div class="section-header">
              <h2>Account</h2>
              <span *ngIf="accountsState().loading">Loading</span>
            </div>

            <label class="field">
              <span>Direct trading account</span>
              <select [ngModel]="selectedAccountId() ?? ''" (ngModelChange)="selectAccount($event)">
                <option value="" disabled>Select account</option>
                <option *ngFor="let account of accountsState().accounts" [value]="account.accountId">
                  {{ account.clientName }} · #{{ account.accountId }}
                </option>
              </select>
            </label>

            <p class="error-copy" *ngIf="accountsState().errorMessage as accountError">{{ accountError }}</p>

            <ng-container *ngIf="selectedAccount() as account">
              <div class="summary-grid compact">
                <div>
                  <span>Client</span>
                  <strong>{{ account.clientName }}</strong>
                </div>
                <div>
                  <span>Cash balance</span>
                  <strong>{{ account.cashBalance | currency : account.currency : 'symbol' : '1.2-2' }}</strong>
                </div>
              </div>
            </ng-container>
          </div>

          <div class="panel-section" *ngIf="overviewState().overview as overview">
            <div class="section-header">
              <h2>Collateral</h2>
              <span>{{ overview.settlementAsset }}</span>
            </div>
            <div class="summary-grid">
              <div>
                <span>Equity</span>
                <strong>{{ overview.accountEquityUsd | currency : 'USD' : 'symbol' : '1.2-2' }}</strong>
              </div>
              <div>
                <span>Available</span>
                <strong>{{ overview.availableCollateralUsd | currency : 'USD' : 'symbol' : '1.2-2' }}</strong>
              </div>
              <div>
                <span>Insurance fund</span>
                <strong>{{ overview.insuranceFundUsd | currency : 'USD' : 'symbol' : '1.0-0' }}</strong>
              </div>
              <div>
                <span>Maint. margin</span>
                <strong>{{ overview.maintenanceMarginRate | percent : '1.2-2' }}</strong>
              </div>
            </div>
          </div>

          <div class="panel-section" *ngIf="overviewState().overview?.position as position; else noPosition">
            <div class="section-header">
              <h2>Open position</h2>
              <span [class.positive]="position.unrealizedPnlUsd >= 0" [class.negative]="position.unrealizedPnlUsd < 0">
                {{ position.side }}
              </span>
            </div>
            <div class="summary-grid">
              <div>
                <span>Size</span>
                <strong>{{ position.signedQuantity | number : '1.3-3' }} ETH</strong>
              </div>
              <div>
                <span>Entry / Mark</span>
                <strong>{{ position.entryPrice | currency : 'USD' : 'symbol' : '1.2-2' }}</strong>
                <small>{{ position.markPrice | currency : 'USD' : 'symbol' : '1.2-2' }}</small>
              </div>
              <div>
                <span>Unrealized PnL</span>
                <strong [class.positive]="position.unrealizedPnlUsd >= 0" [class.negative]="position.unrealizedPnlUsd < 0">
                  {{ position.unrealizedPnlUsd | currency : 'USD' : 'symbol' : '1.2-2' }}
                </strong>
              </div>
              <div>
                <span>Liquidation</span>
                <strong>{{ position.liquidationPrice | currency : 'USD' : 'symbol' : '1.2-2' }}</strong>
              </div>
            </div>
          </div>

          <ng-template #noPosition>
            <div class="panel-section">
              <div class="section-header">
                <h2>Open position</h2>
              </div>
              <p class="muted-copy">No ETH perpetual position is open on the selected account yet.</p>
            </div>
          </ng-template>

          <div class="panel-section" *ngIf="overviewState().overview as overview">
            <div class="section-header">
              <h2>Recent orders</h2>
            </div>
            <div class="order-list" *ngIf="overview.recentOrders.length; else noOrders">
              <article class="order-row" *ngFor="let order of overview.recentOrders">
                <div>
                  <strong>{{ order.side }}</strong>
                  <span>{{ order.quantity | number : '1.3-3' }} ETH · {{ order.orderType }}</span>
                </div>
                <div class="order-meta">
                  <strong>{{ order.status }}</strong>
                  <span>{{ order.createdAt | date : 'shortTime' }}</span>
                </div>
              </article>
            </div>
            <ng-template #noOrders>
              <p class="muted-copy">No perpetual orders have been recorded for this account.</p>
            </ng-template>
          </div>
        </aside>

        <section class="panel chart-panel">
          <div class="chart-header" *ngIf="overviewState().overview as overview">
            <div>
              <h2>{{ overview.assetSymbol }}/{{ overview.quoteCurrency }}</h2>
              <p>Index {{ overview.indexPrice | currency : 'USD' : 'symbol' : '1.2-2' }} · Basis {{ overview.basisBps | number : '1.1-1' }} bps</p>
            </div>
            <div class="quote-ladder">
              <div>
                <span>Bid</span>
                <strong>{{ overview.bestBid | currency : 'USD' : 'symbol' : '1.2-2' }}</strong>
              </div>
              <div>
                <span>Ask</span>
                <strong>{{ overview.bestAsk | currency : 'USD' : 'symbol' : '1.2-2' }}</strong>
              </div>
            </div>
          </div>

          <div class="loading-panel" *ngIf="overviewState().loading">Loading ETH perpetual market...</div>
          <p class="error-copy" *ngIf="overviewState().errorMessage as overviewError">{{ overviewError }}</p>

          <app-tradingview-chart [symbol]="tradingViewSymbol()"></app-tradingview-chart>

          <div class="chart-footer" *ngIf="overviewState().overview as overview">
            <div class="footer-stat">
              <span>Premium index</span>
              <strong>{{ overview.premiumIndex | percent : '1.4-4' }}</strong>
            </div>
            <div class="footer-stat">
              <span>Annualized funding</span>
              <strong>{{ overview.annualizedFundingRate | percent : '1.2-2' }}</strong>
            </div>
            <div class="footer-stat">
              <span>Long OI</span>
              <strong>{{ overview.longOpenInterestUsd | currency : 'USD' : 'symbol' : '1.0-0' }}</strong>
            </div>
            <div class="footer-stat">
              <span>Short OI</span>
              <strong>{{ overview.shortOpenInterestUsd | currency : 'USD' : 'symbol' : '1.0-0' }}</strong>
            </div>
          </div>
        </section>

        <aside class="panel ticket-panel">
          <div class="section-header">
            <h2>Place order</h2>
            <span>ETH-PERP</span>
          </div>

          <div class="segmented-control">
            <button type="button" [class.active]="orderSide() === 'BUY'" (click)="orderSide.set('BUY')">Buy / Long</button>
            <button type="button" [class.active]="orderSide() === 'SELL'" (click)="orderSide.set('SELL')">Sell / Short</button>
          </div>

          <div class="segmented-control compact-toggle">
            <button type="button" [class.active]="orderType() === 'MARKET'" (click)="orderType.set('MARKET')">Market</button>
            <button type="button" [class.active]="orderType() === 'LIMIT'" (click)="orderType.set('LIMIT')">Limit</button>
          </div>

          <label class="field">
            <span>Quantity (ETH)</span>
            <input type="number" min="0.001" step="0.001" [ngModel]="quantity()" (ngModelChange)="quantity.set(coerceNumber($event, 0.25, 0.001))" />
          </label>

          <label class="field">
            <span>Leverage</span>
            <input type="range" min="1" [max]="maxLeverage()" step="1" [ngModel]="leverage()" (ngModelChange)="leverage.set(coerceNumber($event, 5, 1))" />
            <strong>{{ leverage() }}x</strong>
          </label>

          <label class="field" *ngIf="orderType() === 'LIMIT'">
            <span>Limit price</span>
            <input type="number" min="0.01" step="0.01" [ngModel]="limitPrice() ?? ''" (ngModelChange)="limitPrice.set(coerceNullableNumber($event))" />
          </label>

          <label class="checkbox-field">
            <input type="checkbox" [ngModel]="reduceOnly()" (ngModelChange)="reduceOnly.set(!!$event)" />
            <span>Reduce-only</span>
          </label>

          <div class="preview-grid" *ngIf="tradePreview() as preview">
            <div>
              <span>Est. fill</span>
              <strong>{{ preview.executionPrice | currency : 'USD' : 'symbol' : '1.2-2' }}</strong>
            </div>
            <div>
              <span>Notional</span>
              <strong>{{ preview.notionalUsd | currency : 'USD' : 'symbol' : '1.2-2' }}</strong>
            </div>
            <div>
              <span>Initial margin</span>
              <strong>{{ preview.initialMarginUsd | currency : 'USD' : 'symbol' : '1.2-2' }}</strong>
            </div>
            <div>
              <span>Est. fee</span>
              <strong>{{ preview.feeUsd | currency : 'USD' : 'symbol' : '1.2-2' }}</strong>
            </div>
          </div>

          <p class="muted-copy">
            Orders are priced from the live ETH index and the database-backed perpetual state.
            Limit orders only fill when they cross the current book.
          </p>

          <div class="banner" *ngIf="banner() as banner" [class.banner-success]="banner.tone === 'success'" [class.banner-error]="banner.tone === 'error'">
            {{ banner.message }}
          </div>

          <button class="submit-button" type="button" [disabled]="!canSubmitOrder()" (click)="submitOrder()">
            {{ isSubmitting() ? 'Submitting…' : orderSide() === 'BUY' ? 'Buy / Long ETH-PERP' : 'Sell / Short ETH-PERP' }}
          </button>
        </aside>
      </section>
    </main>
  `,
  styles: [`
    .perp-shell { width: min(1550px, 100%); padding-top: 2rem; text-align: left; }
    .market-topbar, .trade-layout { display: grid; gap: 1rem; }
    .market-topbar { grid-template-columns: 1.4fr repeat(4, minmax(0, 1fr)); align-items: stretch; }
    .market-title, .panel, .stat-chip { background: linear-gradient(180deg, rgba(15, 23, 42, 0.96), rgba(17, 24, 39, 0.92)); border: 1px solid rgba(96, 165, 250, 0.14); border-radius: 1rem; box-shadow: 0 18px 36px rgba(0, 0, 0, 0.35); }
    .market-title, .stat-chip, .panel { padding: 1rem 1.1rem; }
    .market-title h1, .section-header h2, .chart-header h2 { margin: 0; color: var(--text-primary); font-weight: 800; letter-spacing: -0.02em; }
    .market-title span, .stat-chip span, .muted-copy, .chart-header p { color: var(--text-secondary); }
    .stat-chip { display: grid; gap: 0.35rem; }
    .stat-chip label, .summary-grid span, .field span, .preview-grid span, .footer-stat span { color: #93c5fd; font-size: 0.8rem; text-transform: uppercase; letter-spacing: 0.05rem; }
    .stat-chip strong, .summary-grid strong, .preview-grid strong, .footer-stat strong { color: var(--text-primary); font-size: 1.1rem; }
    .trade-layout { grid-template-columns: 300px minmax(0, 1fr) 360px; margin-top: 1rem; }
    .panel { display: grid; gap: 1rem; min-height: 0; }
    .sidebar, .ticket-panel { align-content: start; }
    .panel-section { display: grid; gap: 0.8rem; padding-bottom: 1rem; border-bottom: 1px solid rgba(148, 163, 184, 0.1); }
    .panel-section:last-child { border-bottom: 0; padding-bottom: 0; }
    .section-header { display: flex; justify-content: space-between; gap: 0.75rem; align-items: center; }
    .section-header span, .order-row span, .order-meta span { color: var(--text-secondary); font-size: 0.85rem; }
    .field, .checkbox-field { display: grid; gap: 0.45rem; }
    .field input, .field select { width: 100%; border: 1px solid rgba(148, 163, 184, 0.2); border-radius: 0.8rem; background: rgba(2, 6, 23, 0.72); color: var(--text-primary); padding: 0.8rem 0.9rem; }
    .field input[type='range'] { padding: 0; }
    .checkbox-field { grid-template-columns: auto 1fr; align-items: center; }
    .summary-grid, .preview-grid, .chart-footer { display: grid; gap: 0.8rem; }
    .summary-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
    .summary-grid.compact { grid-template-columns: 1fr; }
    .summary-grid small { color: var(--text-secondary); }
    .order-list { display: grid; gap: 0.65rem; }
    .order-row, .quote-ladder, .segmented-control button, .banner { border-radius: 0.85rem; }
    .order-row { display: flex; justify-content: space-between; gap: 0.75rem; padding: 0.75rem 0.85rem; background: rgba(15, 23, 42, 0.6); }
    .order-meta { text-align: right; }
    .chart-panel { grid-template-rows: auto 1fr auto; }
    app-tradingview-chart { display: block; min-height: 44rem; }
    .chart-header, .quote-ladder, .chart-footer { display: flex; justify-content: space-between; gap: 1rem; align-items: center; }
    .quote-ladder { background: rgba(15, 23, 42, 0.58); padding: 0.85rem 1rem; }
    .quote-ladder div, .footer-stat { display: grid; gap: 0.2rem; }
    .footer-stat { min-width: 0; }
    .segmented-control { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0.55rem; }
    .segmented-control button, .submit-button { border: 1px solid rgba(96, 165, 250, 0.18); background: rgba(15, 23, 42, 0.88); color: var(--text-primary); padding: 0.9rem 1rem; font-weight: 700; cursor: pointer; }
    .segmented-control button.active { background: linear-gradient(135deg, rgba(37, 99, 235, 0.92), rgba(29, 78, 216, 0.92)); }
    .compact-toggle button.active { background: rgba(96, 165, 250, 0.18); }
    .preview-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); padding: 0.9rem; border-radius: 0.9rem; background: rgba(15, 23, 42, 0.55); }
    .submit-button { background: linear-gradient(135deg, #2563eb, #1d4ed8); }
    .submit-button:disabled { opacity: 0.5; cursor: not-allowed; }
    .banner { padding: 0.8rem 0.9rem; background: rgba(96, 165, 250, 0.12); color: #dbeafe; }
    .banner-success { background: rgba(34, 197, 94, 0.14); color: #bbf7d0; }
    .banner-error, .error-copy { background: rgba(248, 113, 113, 0.14); color: #fecaca; }
    .error-copy { margin: 0; padding: 0.7rem 0.8rem; border-radius: 0.8rem; }
    .loading-panel { color: var(--text-secondary); }
    .positive { color: #4ade80 !important; }
    .negative { color: #f87171 !important; }
    @media (max-width: 1280px) {
      .market-topbar, .trade-layout { grid-template-columns: 1fr; }
      app-tradingview-chart { min-height: 34rem; }
    }
  `],
})
export class PerpetualFuturesPageComponent {
  protected readonly marketSymbol = 'ETH-PERP';
  protected readonly orderSide = signal<'BUY' | 'SELL'>('BUY');
  protected readonly orderType = signal<'MARKET' | 'LIMIT'>('MARKET');
  protected readonly quantity = signal(0.5);
  protected readonly leverage = signal(5);
  protected readonly limitPrice = signal<number | null>(null);
  protected readonly reduceOnly = signal(false);
  protected readonly selectedAccountId = signal<number | null>(null);
  protected readonly selectedClientId = signal<number | null>(null);
  protected readonly banner = signal<BannerState | null>(null);
  protected readonly isSubmitting = signal(false);
  private readonly reloadToken = signal(0);

  private readonly perpetualFuturesService = inject(PerpetualFuturesService);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly isBrowser = isPlatformBrowser(this.platformId);

  protected readonly accountsState = toSignal(
    (this.isBrowser
      ? this.perpetualFuturesService.getAccounts().pipe(
          map((accounts): AccountsState => ({
            accounts,
            loading: false,
            errorMessage: accounts.length ? null : 'No DIRECT_TRADING accounts are available in the database.',
          })),
          startWith({ accounts: [], loading: true, errorMessage: null } satisfies AccountsState),
          catchError((error: unknown) =>
            of({
              accounts: [],
              loading: false,
              errorMessage: this.describeError(error, 'Unable to load perpetual trading accounts.'),
            } satisfies AccountsState),
          ),
        )
      : of({ accounts: [], loading: false, errorMessage: null } satisfies AccountsState)),
    { initialValue: { accounts: [], loading: true, errorMessage: null } satisfies AccountsState },
  );

  protected readonly overviewState = toSignal(
    (this.isBrowser
      ? toObservable(
          computed(() => ({
            clientId: this.selectedClientId(),
            accountId: this.selectedAccountId(),
            refresh: this.reloadToken(),
          })),
        ).pipe(
          switchMap(({ clientId, accountId }) => {
            if (!clientId || !accountId) {
              return of({
                overview: null,
                loading: false,
                errorMessage: this.accountsState().loading ? null : 'Select a direct trading account to trade ETH-PERP.',
              } satisfies OverviewState);
            }

            return this.perpetualFuturesService.getMarketOverview(this.marketSymbol, clientId, accountId).pipe(
              map((overview): OverviewState => ({
                overview,
                loading: false,
                errorMessage: null,
              })),
              startWith({ overview: null, loading: true, errorMessage: null } satisfies OverviewState),
              catchError((error: unknown) =>
                of({
                  overview: null,
                  loading: false,
                  errorMessage: this.describeError(error, 'Unable to load Ethereum perpetual market data.'),
                } satisfies OverviewState),
              ),
            );
          }),
        )
      : of({ overview: null, loading: false, errorMessage: null } satisfies OverviewState)),
    { initialValue: { overview: null, loading: false, errorMessage: null } satisfies OverviewState },
  );

  protected readonly selectedAccount = computed(() =>
    this.accountsState().accounts.find((account) => account.accountId === this.selectedAccountId()) ?? null,
  );

  protected readonly maxLeverage = computed(() => this.overviewState().overview?.maxLeverage ?? 50);

  protected readonly tradingViewSymbol = computed(
    () => this.overviewState().overview?.tradingViewSymbol ?? 'BITSTAMP:ETHUSD',
  );

  protected readonly tradePreview = computed(() => {
    const overview = this.overviewState().overview;
    if (!overview) {
      return null;
    }

    const executionPrice =
      this.orderType() === 'MARKET'
        ? this.orderSide() === 'BUY'
          ? overview.bestAsk
          : overview.bestBid
        : this.limitPrice() ?? overview.markPrice;
    const notionalUsd = executionPrice * this.quantity();
    const initialMarginUsd = notionalUsd / Math.max(this.leverage(), 1);
    const feeUsd = notionalUsd * overview.takerFeeRate;

    return {
      executionPrice,
      notionalUsd,
      initialMarginUsd,
      feeUsd,
    };
  });

  constructor() {
    effect(() => {
      const accounts = this.accountsState().accounts;
      if (!accounts.length || this.selectedAccountId() !== null) {
        return;
      }

      this.selectedAccountId.set(accounts[0].accountId);
      this.selectedClientId.set(accounts[0].clientId);
    });
  }

  protected selectAccount(value: string): void {
    const accountId = Number(value);
    const account = this.accountsState().accounts.find((item) => item.accountId === accountId);
    if (!account) {
      return;
    }

    this.selectedAccountId.set(account.accountId);
    this.selectedClientId.set(account.clientId);
    this.banner.set(null);
    this.reloadToken.update((current) => current + 1);
  }

  protected canSubmitOrder(): boolean {
    return !this.isSubmitting() && !!this.selectedAccountId() && !!this.selectedClientId() && this.quantity() > 0;
  }

  protected submitOrder(): void {
    const clientId = this.selectedClientId();
    const accountId = this.selectedAccountId();
    if (!clientId || !accountId) {
      this.banner.set({ tone: 'error', message: 'Select a direct trading account before placing an order.' });
      return;
    }

    this.isSubmitting.set(true);
    this.banner.set({ tone: 'info', message: 'Submitting order to the perpetual futures engine…' });

    this.perpetualFuturesService
      .submitOrder({
        clientId,
        accountId,
        marketSymbol: this.marketSymbol,
        side: this.orderSide(),
        orderType: this.orderType(),
        quantity: this.quantity(),
        leverage: this.leverage(),
        limitPrice: this.orderType() === 'LIMIT' ? this.limitPrice() : null,
        reduceOnly: this.reduceOnly(),
      })
      .subscribe({
        next: (submission: PerpetualOrderSubmission) => {
          this.banner.set({
            tone: submission.order.status === 'FILLED' ? 'success' : 'error',
            message:
              submission.order.status === 'FILLED'
                ? `${submission.order.side} ${submission.order.quantity.toFixed(3)} ETH filled at $${(submission.order.averageFillPrice ?? submission.order.markPrice).toFixed(2)}.`
                : submission.order.rejectionReason ?? 'Order was rejected.',
          });
          this.reloadToken.update((current) => current + 1);
        },
        error: (error: unknown) => {
          this.banner.set({
            tone: 'error',
            message: this.describeError(error, 'Unable to submit the perpetual order.'),
          });
          this.isSubmitting.set(false);
        },
        complete: () => {
          this.isSubmitting.set(false);
        },
      });
  }

  protected coerceNumber(value: unknown, fallback: number, minimum: number): number {
    const numericValue = typeof value === 'number' ? value : Number(value);
    if (!Number.isFinite(numericValue)) {
      return fallback;
    }
    return Math.max(minimum, numericValue);
  }

  protected coerceNullableNumber(value: unknown): number | null {
    if (value === '' || value === null || value === undefined) {
      return null;
    }

    const numericValue = typeof value === 'number' ? value : Number(value);
    return Number.isFinite(numericValue) && numericValue > 0 ? numericValue : null;
  }

  private describeError(error: unknown, fallback: string): string {
    if (
      typeof error === 'object' &&
      error !== null &&
      'error' in error &&
      typeof (error as { error?: unknown }).error === 'object' &&
      (error as { error?: Record<string, unknown> }).error?.['detail']
    ) {
      return String((error as { error: Record<string, unknown> }).error['detail']);
    }

    if (error instanceof Error && error.message) {
      return error.message;
    }

    return fallback;
  }
}
