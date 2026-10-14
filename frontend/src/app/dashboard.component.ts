import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin, interval, of, catchError } from 'rxjs';
import {
  Instrument, MarketApi, Order, OrderApi, PortfolioApi, PortfolioValuation, Quote, WatchlistApi, WatchlistItem, errorMessage,
} from './api.service';
import { NavHeaderComponent } from './nav-header.component';
import { TradeTicketComponent } from './trade-ticket.component';
import { InstrumentSearchComponent } from './instrument-search.component';
import {
  displaySymbol, isOpen, money, orderStatus, percent, price, quantity, segmentLabel, signedMoney, trendClass,
} from './format';

@Component({
  selector: 'app-dashboard',
  imports: [RouterLink, NavHeaderComponent, TradeTicketComponent, InstrumentSearchComponent],
  template: `
    <app-nav-header [cash]="valuation()?.cashBalance ?? null" [currency]="currency()" />

    <main class="page">
      @if (loadError()) { <p class="alert error" role="alert">{{ loadError() }}</p> }

      <section class="card hero">
        <div class="hero-row">
          <div>
            <p class="label">Portfolio value</p>
            @if (valuation(); as v) {
              <p class="num hero-value">{{ fmt.money(v.totalValue, v.currency) }}</p>
              <p class="num change {{ fmt.trendClass(v.dayChange) }}">
                {{ fmt.signedMoney(v.dayChange, v.currency) }} <span class="muted">today</span>
              </p>
              @if (!v.complete) { <p class="hint">Some holdings could not be priced just now and are left out of this total.</p> }
            } @else {
              <p class="skeleton">Loading your portfolio…</p>
            }
          </div>
          <button type="button" class="btn btn-primary" (click)="openTrade(defaultSymbol())">Trade</button>
        </div>
      </section>

      <div class="stat-row">
        <div class="card">
          <p class="label">Cash balance</p>
          <p class="num stat-value">{{ fmt.money(valuation()?.cashBalance, currency()) }}</p>
          <p class="hint">Available to trade now</p>
        </div>
        <div class="card">
          <p class="label">Total return</p>
          <p class="num stat-value {{ fmt.trendClass(valuation()?.totalReturn) }}">{{ fmt.signedMoney(valuation()?.totalReturn, currency()) }}</p>
          <p class="hint">Against the {{ fmt.money(valuation()?.netDeposits, currency()) }} you've put in</p>
        </div>
        <div class="card">
          <p class="label">Open orders</p>
          <p class="num stat-value">{{ openOrders().length }}</p>
          <a routerLink="/blotter" class="link">View blotter →</a>
        </div>
      </div>

      <section class="card flush">
        <div class="card-head"><h2>Holdings</h2></div>
        @if (!valuation()) {
          <p class="empty">Loading holdings…</p>
        } @else if (valuation()!.positions.length === 0) {
          <p class="empty">You don't hold anything yet. <a routerLink="/trade" class="link">Browse markets</a> to place your first trade.</p>
        } @else {
          <div class="table-wrap">
            <table class="data">
              <thead><tr><th>Instrument</th><th class="right">Qty</th><th class="right">Avg cost</th><th class="right">Price</th><th class="right">Market value</th><th class="right">Return</th><th class="right">Today</th><th></th></tr></thead>
              <tbody>
                @for (h of valuation()!.positions; track h.symbol) {
                  <tr>
                    <td><div class="instrument-cell"><span class="symbol">{{ fmt.displaySymbol(h.symbol) }}</span><span class="sub">{{ h.name }} · {{ fmt.segmentLabel(h.segment) }}</span></div></td>
                    <td class="num right">{{ fmt.quantity(h.quantity) }}</td>
                    <td class="num right">{{ fmt.money(h.averageCost, currency()) }}</td>
                    <td class="num right">{{ h.priced ? fmt.price(h.price, h.instrumentCurrency) : 'Unavailable' }}</td>
                    <td class="num right bold">{{ fmt.money(h.marketValue, currency()) }}</td>
                    <td class="num right {{ fmt.trendClass(h.unrealizedReturn) }}">{{ fmt.signedMoney(h.unrealizedReturn, currency()) }}</td>
                    <td class="num right {{ fmt.trendClass(h.changePercent) }}">{{ fmt.percent(h.changePercent) }}</td>
                    <td class="right"><button type="button" class="link" (click)="openTrade(h.symbol)">Trade<span class="sr-only"> {{ h.symbol }}</span></button></td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </section>

      <div class="two-col">
        <section class="card flush">
          <div class="card-head"><h2>Watchlist</h2><a routerLink="/trade" class="link">Add from markets →</a></div>
          @if (watchlist().length === 0) {
            <p class="empty">Follow instruments you're interested in. Tap the star on any instrument in Markets.</p>
          } @else {
            <div class="table-wrap">
              <table class="data">
                <tbody>
                  @for (w of watchlist(); track w.instrument.symbol) {
                    <tr>
                      <td><div class="instrument-cell"><span class="symbol">{{ fmt.displaySymbol(w.instrument.symbol) }}</span><span class="sub">{{ w.instrument.name }}</span></div></td>
                      <td class="num right">{{ quoteText(w.instrument) }}</td>
                      <td class="num right {{ fmt.trendClass(watchQuotes()[w.instrument.symbol]?.changePercent) }}">{{ fmt.percent(watchQuotes()[w.instrument.symbol]?.changePercent) }}</td>
                      <td class="right"><button type="button" class="link" (click)="openTrade(w.instrument.symbol)">Trade<span class="sr-only"> {{ w.instrument.symbol }}</span></button></td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
        </section>

        <section class="card flush">
          <div class="card-head"><h2>Recent activity</h2><a routerLink="/blotter" class="link">View all →</a></div>
          @if (orders().length === 0) {
            <p class="empty">No orders yet.</p>
          } @else {
            <div class="table-wrap">
              <table class="data">
                <tbody>
                  @for (o of orders().slice(0, 5); track o.orderId) {
                    <tr>
                      <td><div class="instrument-cell"><span class="symbol">{{ fmt.displaySymbol(o.symbol) }}</span><span class="sub side" [class.buy]="o.side === 'BUY'" [class.sell]="o.side === 'SELL'">{{ o.side === 'BUY' ? 'Buy' : 'Sell' }} {{ fmt.quantity(o.quantity) }}</span></div></td>
                      <td class="num right">{{ fmt.money(o.settlementAmount, o.settlementCurrency) }}</td>
                      <td class="right"><span class="badge {{ fmt.orderStatus(o).cls }}">{{ fmt.orderStatus(o).label }}</span></td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
        </section>
      </div>
    </main>

    @if (tradeInstrument(); as instrument) {
      <div class="overlay" (click)="closeTrade()" aria-hidden="true"></div>
      <div class="sheet" role="dialog" aria-modal="true" [attr.aria-label]="'Trade ' + instrument.symbol" (keydown.escape)="closeTrade()">
        <div class="sheet-head">
          <div>
            <p class="sheet-eyebrow">{{ fmt.segmentLabel(instrument.segment) }}</p>
            <h2>{{ fmt.displaySymbol(instrument.symbol) }} <span class="muted name">{{ instrument.name }}</span></h2>
          </div>
          <button type="button" class="close" (click)="closeTrade()" aria-label="Close">✕</button>
        </div>
        <div class="picker">
          <app-instrument-search label="Trade something else" [instruments]="instruments()"
                                 (picked)="tradeInstrument.set($event)" (catalogueChanged)="instruments.set($event)" />
        </div>
        <div class="sheet-body">
          <app-trade-ticket [instrument]="instrument" (settled)="refresh()" />
        </div>
      </div>
    }
  `,
  styles: [`
    .hero { padding: 32px; }
    .hero-row { display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 16px; }
    .hero-value { font-size: 40px; font-weight: 700; font-family: var(--font-display); margin: 0; }
    .change { font-size: 14px; font-weight: 600; margin: 8px 0 0; }
    .two-col { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(420px, 100%), 1fr)); gap: 24px; }
    .sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); }
    .overlay { position: fixed; inset: 0; background: rgba(11, 21, 48, .45); z-index: 40; }
    .sheet {
      position: fixed; z-index: 41; left: 50%; bottom: 0; transform: translateX(-50%); width: min(560px, 100%);
      max-height: 90vh; overflow-y: auto; background: var(--surface); border-radius: 24px 24px 0 0;
      box-shadow: 0 -24px 60px rgba(11,21,48,.35);
    }
    .sheet-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; padding: 22px 28px 16px; border-bottom: 1px solid var(--border); }
    .sheet-eyebrow { font-size: 12px; font-weight: 600; color: var(--text-muted); text-transform: uppercase; letter-spacing: .04em; margin: 0 0 4px; }
    .sheet h2 { font-size: 20px; }
    .name { font-size: 14px; font-weight: 400; font-family: var(--font-body); }
    .close { width: 36px; height: 36px; border-radius: 999px; border: none; background: var(--surface-alt); color: var(--text-muted); cursor: pointer; }
    .sheet-body { padding: 22px 28px 28px; }
    .picker { padding: 14px 28px 0; }
  `],
})
export class DashboardComponent implements OnInit {
  private readonly portfolio = inject(PortfolioApi);
  private readonly orderApi = inject(OrderApi);
  private readonly market = inject(MarketApi);
  private readonly watchlistApi = inject(WatchlistApi);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly fmt = { money, signedMoney, price, quantity, percent, trendClass, segmentLabel, displaySymbol, orderStatus };

  protected readonly valuation = signal<PortfolioValuation | null>(null);
  protected readonly orders = signal<Order[]>([]);
  protected readonly watchlist = signal<WatchlistItem[]>([]);
  protected readonly watchQuotes = signal<Record<string, Quote>>({});
  protected readonly instruments = signal<Instrument[]>([]);
  protected readonly tradeInstrument = signal<Instrument | null>(null);
  protected readonly loadError = signal('');
  protected readonly currency = computed(() => this.valuation()?.currency ?? 'USD');
  protected readonly openOrders = computed(() => this.orders().filter(isOpen));
  protected readonly defaultSymbol = computed(() => this.valuation()?.positions[0]?.symbol ?? 'AAPL');

  ngOnInit(): void {
    this.refresh();
    this.market.instruments().subscribe({ next: (list) => this.instruments.set(list), error: () => undefined });
    // Orders in flight are followed every few seconds; prices refresh every 30
    const fast = interval(3000).subscribe(() => {
      if (this.openOrders().length > 0) this.loadOrders(true);
    });
    const slow = interval(30000).subscribe(() => this.refresh());
    this.destroyRef.onDestroy(() => {
      fast.unsubscribe();
      slow.unsubscribe();
    });
  }

  refresh(): void {
    this.portfolio.valuation().subscribe({
      next: (v) => {
        this.valuation.set(v);
        this.loadError.set('');
      },
      error: (e) => this.loadError.set(errorMessage(e, 'Your portfolio could not be loaded.')),
    });
    this.loadOrders(false);
    this.watchlistApi.list().subscribe({ next: (items) => this.loadWatchlist(items), error: () => undefined });
  }

  openTrade(symbol: string): void {
    const instrument = this.instruments().find((i) => i.symbol === symbol)
      ?? this.watchlist().find((w) => w.instrument.symbol === symbol)?.instrument;
    if (instrument) this.tradeInstrument.set(instrument);
  }

  closeTrade(): void {
    this.tradeInstrument.set(null);
  }

  protected quoteText(instrument: Instrument): string {
    const q = this.watchQuotes()[instrument.symbol];
    return q ? price(q.price, q.currency ?? instrument.baseCurrency) : '—';
  }

  private loadOrders(settleCheck: boolean): void {
    const openBefore = this.openOrders().length;
    this.orderApi.list().subscribe({
      next: (list) => {
        this.orders.set(list);
        // An order just settled: cash and holdings changed
        if (settleCheck && this.openOrders().length < openBefore) {
          this.portfolio.valuation().subscribe({ next: (v) => this.valuation.set(v), error: () => undefined });
        }
      },
      error: () => undefined,
    });
  }

  private loadWatchlist(items: WatchlistItem[]): void {
    this.watchlist.set(items);
    if (items.length === 0) return;
    forkJoin(items.map((w) => this.market.quote(w.instrument.symbol).pipe(catchError(() => of(null))))).subscribe((quotes) => {
      const bySymbol: Record<string, Quote> = {};
      quotes.forEach((q, i) => { if (q) bySymbol[items[i].instrument.symbol] = q; });
      this.watchQuotes.set(bySymbol);
    });
  }
}
