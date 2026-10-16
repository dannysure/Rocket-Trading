import { Component, InjectionToken, OnDestroy, OnInit, PLATFORM_ID, computed, inject, signal } from '@angular/core';
import { DatePipe, isPlatformBrowser } from '@angular/common';
import { EMPTY, Observable, Subject, Subscription, catchError, finalize, interval, merge, retry, switchMap, throwError, timer } from 'rxjs';
import { errorMessage } from './api.service';
import { NavHeaderComponent } from './nav-header.component';
import { money, newIdempotencyKey, price, quantity, signedMoney, trendClass } from './format';
import { PerpAccount, PerpFunding, PerpMarket, PerpOrder, PerpOrderRequest, PerpPosition, PerpsApi, PerpsStream, PerpStreamError, PerpTicker } from './perps.service';
import { Candle, OrderSide, buildDepthLadder, estimateFillPrice, formatCountdown, markToMarket, maxOrderSize, mergeTick, previewOrder } from './perps.math';
import { ChartLine, PerpsChartComponent } from './perps-chart.component';

/** How often the account, orders and funding refresh; 0 disables polling (tests). Prices stream continuously. */
export const PERPS_POLL_MS = new InjectionToken<number>('PERPS_POLL_MS', { factory: () => 3000 });

const LIVE_SESSION_MS = 10 * 60_000;
const MAX_STREAM_RETRY_MS = 30_000;
const QUICK_AMOUNTS = [25, 50, 75, 100];

@Component({
  selector: 'app-perps',
  imports: [DatePipe, NavHeaderComponent, PerpsChartComponent],
  template: `
    <app-nav-header [cash]="account()?.availableCollateral ?? null" [currency]="currency()" />
    <main class="page wide">
      <h1 class="page-title">Perpetuals</h1>
      <p class="page-subtitle">Go long or short on crypto with leverage. Isolated margin: a position can only lose the margin you put on it.</p>

      <div class="live-controls">
        <button type="button" class="btn btn-outline" data-testid="live-toggle" (click)="liveActive() ? stopLiveUpdates() : startLiveUpdates()">
          {{ liveActive() ? 'Stop live updates' : 'Start live updates' }}
        </button>
        <span role="status" data-testid="live-status">{{ liveActive() ? 'Live · pauses in ' + liveRemaining() : 'Dormant · live updates paused' }}</span>
        <p class="hint">Live updates stream prices for 10 minutes. Background liquidation checks and funding continue while dormant.</p>
      </div>

      <div class="pill-row markets" role="tablist" aria-label="Perpetual markets">
        @for (m of markets(); track m.symbol) {
          <button type="button" role="tab" class="pill" [attr.data-testid]="'market-' + m.symbol"
                  [class.active]="m.symbol === selectedSymbol()" [attr.aria-selected]="m.symbol === selectedSymbol()"
                  (click)="selectMarket(m.symbol)">{{ m.symbol }} <span class="pill-lev">{{ m.maxLeverage }}x</span></button>
        }
      </div>

      <section class="card market-bar">
        <div>
          <p class="eyebrow">Perpetual · Isolated margin</p>
          <h2 data-testid="market-symbol">{{ selectedSymbol() }}</h2>
        </div>
        <dl class="stats">
          <div><dt title="Oracle price plus the premium set by the long/short open-interest imbalance">Mark</dt><dd class="num" data-testid="mark-price">{{ fmt.price(ticker()?.markPrice, currency()) }}</dd></div>
          <div><dt title="Spot index: the live Coinbase mid price">Oracle</dt><dd class="num" data-testid="oracle-price">{{ fmt.price(ticker()?.oraclePrice, currency()) }}</dd></div>
          <div><dt title="How far the mark is from the oracle; positive while longs outweigh shorts">Premium</dt>
            <dd class="num" data-testid="premium" [class]="fmt.trendClass(ticker()?.premium)">{{ signedRate(ticker()?.premium) }}</dd></div>
          <div>
            <dt [title]="'Rate per ' + fundingInterval() + ', set by the open-interest imbalance. The bigger side pays the smaller one, which pulls the mark back to the oracle.'">Funding ({{ fundingInterval() }}) / next in</dt>
            <dd class="num">
              <span data-testid="funding-rate" [class]="fundingClass()">{{ rate(ticker()?.fundingRate) }}</span>
              <span class="muted" data-testid="funding-countdown"> · {{ countdown() }}</span>
            </dd>
          </div>
          <div><dt title="Total open position size across all traders, long and short">Open interest</dt>
            <dd class="num"><span data-testid="open-interest">{{ fmt.quantity(ticker()?.openInterest) }} {{ baseAsset() }}</span>
              @if (ticker(); as t) { <span class="oi-split muted" data-testid="open-interest-split">L {{ fmt.quantity(t.longOpenInterest) }} · S {{ fmt.quantity(t.shortOpenInterest) }}</span> }</dd></div>
          <div><dt>Spread</dt><dd class="num">{{ fmt.price(ladder()?.spread, currency()) }}</dd></div>
        </dl>
        @if (ticker()?.delayed) { <span class="badge waiting" title="The live price stream is down; prices come from the slower backup provider">Delayed price</span> }
        @if (tickerError()) { <p class="alert error bar-alert" role="alert">{{ tickerError() }}</p> }
      </section>

      <div class="grid">
        <section class="card chart-card">
          <div class="card-head">
            <h2>Price</h2>
            <div class="pill-row">
              @for (option of candleOptions; track option.seconds) {
                <button type="button" class="pill small" [attr.data-testid]="'interval-' + option.label" [class.active]="candleSeconds() === option.seconds"
                        (click)="setCandleInterval(option.seconds)">{{ option.label }}</button>
              }
            </div>
          </div>
          <app-perps-chart [candles]="candles()" [live]="liveCandle()" [lines]="chartLines()"
                           [label]="selectedSymbol() + ' price candles'" data-testid="price-chart" />
          @if (candlesError()) { <p class="alert error" role="alert">{{ candlesError() }}</p> }
          <p class="hint">Candles: {{ baseAsset() }} spot oracle from Coinbase, updated tick by tick while live updates run. Lines mark the perp's mark price and your entry and liquidation prices.</p>
        </section>

        <section class="card book" data-testid="order-book">
          <div class="pill-row tabs">
            <button type="button" class="pill small" [class.active]="bookTab() === 'book'" (click)="bookTab.set('book')">Order book</button>
            <button type="button" class="pill small" [class.active]="bookTab() === 'fills'" (click)="bookTab.set('fills')">Your fills</button>
          </div>
          @if (bookTab() === 'book') {
            <p class="hint book-note" title="Orders fill at the live bid and ask, not against these levels">Simulated depth around the live quote</p>
            @if (ladder(); as l) {
              <div class="level head"><span>Price</span><span>Size</span><span>Total</span></div>
              @for (level of l.asks; track level.price) {
                <div class="level ask num" data-testid="ask-level">
                  <i class="depth" [style.width.%]="level.total / maxDepth() * 100"></i>
                  <span class="down">{{ bookPrice(level.price) }}</span><span>{{ fmt.quantity(level.size) }}</span><span>{{ fmt.quantity(level.total) }}</span>
                </div>
              }
              <div class="level spread num"><span>Spread</span><span>{{ bookPrice(l.spread) }}</span><span>{{ l.spreadPercent.toFixed(3) }}%</span></div>
              @for (level of l.bids; track level.price) {
                <div class="level bid num" data-testid="bid-level">
                  <i class="depth" [style.width.%]="level.total / maxDepth() * 100"></i>
                  <span class="up">{{ bookPrice(level.price) }}</span><span>{{ fmt.quantity(level.size) }}</span><span>{{ fmt.quantity(level.total) }}</span>
                </div>
              }
            } @else {
              <p class="skeleton">Fetching price…</p>
            }
          } @else {
            <div class="level head"><span>Price</span><span>Size</span><span>Time</span></div>
            @for (fill of fills().slice(0, 25); track fill.orderId) {
              <div class="level num">
                <span [class.up]="fill.side === 'BUY'" [class.down]="fill.side === 'SELL'">{{ bookPrice(fill.fillPrice) }}</span>
                <span>{{ fmt.quantity(fill.filledSize) }}</span><span class="muted">{{ fill.submittedAt | date: 'HH:mm:ss' }}</span>
              </div>
            } @empty {
              <p class="empty">No fills yet</p>
            }
          }
        </section>

        <section class="card ticket" aria-label="Order ticket">
          <div class="sides" role="radiogroup" aria-label="Long or short">
            <button type="button" role="radio" class="side-btn buy" [class.active]="side() === 'BUY'" [attr.aria-checked]="side() === 'BUY'" (click)="setSide('BUY')">Buy / Long</button>
            <button type="button" role="radio" class="side-btn sell" data-testid="side-sell" [class.active]="side() === 'SELL'" [attr.aria-checked]="side() === 'SELL'" (click)="setSide('SELL')">Sell / Short</button>
          </div>

          <div class="leverage">
            <label class="field-label" for="perp-leverage">Leverage</label>
            <span class="num bold" data-testid="leverage">{{ leverage() }}x</span>
          </div>
          <input id="perp-leverage" type="range" min="1" [max]="maxLeverage()" [value]="leverage()" [disabled]="!!position()"
                 (input)="setLeverage(+$any($event.target).value)" />
          @if (position()) { <p class="hint">Fixed at {{ leverage() }}x while you hold a {{ selectedSymbol() }} position.</p> }

          <div class="row"><span class="muted">Available to trade</span><span class="num bold">{{ fmt.money(account()?.availableCollateral, currency()) }}</span></div>
          <div class="row"><span class="muted">Current position</span>
            <span class="num bold" [class.up]="position()?.side === 'LONG'" [class.down]="position()?.side === 'SHORT'">{{ positionText() }}</span>
          </div>

          <label class="field-label" for="perp-size">Size ({{ baseAsset() }})</label>
          <input id="perp-size" class="input" data-testid="size-input" type="number" min="0" step="any" inputmode="decimal" placeholder="0.00"
                 [value]="sizeInput()" (input)="setSize($any($event.target).value)" />
          <div class="quick" aria-label="Quick sizes">
            @for (pct of quickAmounts; track pct) {
              <button type="button" class="quick-btn" [attr.data-testid]="'quick-' + pct" (click)="setSizePercent(pct)" [disabled]="!ticker()">{{ pct === 100 ? 'Max' : pct + '%' }}</button>
            }
          </div>

          <label class="check"><input type="checkbox" [checked]="reduceOnly()" (change)="reduceOnly.set($any($event.target).checked)" /> Reduce only</label>

          @if (orderError()) { <p class="alert error" data-testid="order-error" role="alert">{{ orderError() }}</p> }
          @if (orderSuccess()) { <p class="alert info" role="status">{{ orderSuccess() }}</p> }

          <button type="button" class="btn btn-block submit" data-testid="submit-order" [class.sell]="side() === 'SELL'"
                  [disabled]="submitting() || !ticker() || !liveActive()" (click)="submit()">{{ submitting() ? 'Placing order…' : submitLabel() }}</button>

          <dl class="summary">
            <div><dt title="Includes the premium this order pays for moving the open-interest imbalance">Est. fill price</dt>
              <dd class="num" data-testid="fill-price">{{ preview() ? fmt.price(fillPrice(), currency()) : '—' }}</dd></div>
            <div><dt>Liquidation price</dt><dd class="num">{{ preview() ? fmt.price(preview()!.liquidationPrice, currency()) : '—' }}</dd></div>
            <div><dt>Order value</dt><dd class="num" data-testid="order-value">{{ preview() ? fmt.money(preview()!.orderValue, currency()) : '—' }}</dd></div>
            <div><dt>Margin required</dt><dd class="num" data-testid="margin-required">{{ preview() ? fmt.money(preview()!.marginRequired, currency()) : '—' }}</dd></div>
            <div><dt>Estimated fee</dt><dd class="num">{{ preview() ? fmt.money(preview()!.fee, currency()) : '—' }}</dd></div>
            <div><dt>Taker fee</dt><dd class="num">{{ rate(ticker()?.takerFeeRate) }}</dd></div>
          </dl>
        </section>
      </div>

      <div class="grid bottom">
        <section class="card flush activity">
          <div class="card-head">
            <div class="pill-row" role="tablist" aria-label="Perpetuals activity">
              <button type="button" role="tab" class="pill small" [class.active]="bottomTab() === 'positions'" (click)="bottomTab.set('positions')">Positions ({{ account()?.positions?.length ?? 0 }})</button>
              <button type="button" role="tab" class="pill small" [class.active]="bottomTab() === 'trades'" (click)="bottomTab.set('trades')">Trade history</button>
              <button type="button" role="tab" class="pill small" [class.active]="bottomTab() === 'funding'" (click)="bottomTab.set('funding')">Funding history</button>
            </div>
          </div>
          <div class="table-wrap">
            @switch (bottomTab()) {
              @case ('positions') {
                <table class="data">
                  <thead><tr><th>Market</th><th class="right">Size</th><th class="right">Value</th><th class="right">Entry</th><th class="right">Mark</th><th class="right">PnL (ROE)</th><th class="right">Liq. price</th><th class="right">Margin</th><th class="right">Funding</th><th></th></tr></thead>
                  <tbody>
                    @for (p of positions(); track p.positionId) {
                      <tr data-testid="position-row">
                        <td><div class="instrument-cell"><button type="button" class="link symbol" (click)="selectMarket(p.symbol)">{{ p.symbol }}</button>
                          <span class="sub side" [class.buy]="p.side === 'LONG'" [class.sell]="p.side === 'SHORT'">{{ p.side === 'LONG' ? 'Long' : 'Short' }} · {{ p.leverage }}x</span></div></td>
                        <td class="right num">{{ fmt.quantity(p.size) }}</td>
                        <td class="right num">{{ fmt.money(p.positionValue, currency()) }}</td>
                        <td class="right num">{{ fmt.price(p.entryPrice, currency()) }}</td>
                        <td class="right num">{{ fmt.price(p.markPrice, currency()) }}</td>
                        <td class="right num {{ fmt.trendClass(p.unrealizedPnl) }}">{{ fmt.signedMoney(p.unrealizedPnl, currency()) }}@if (p.returnOnEquity !== null) { <span class="sub-num">({{ roe(p.returnOnEquity) }})</span> }</td>
                        <td class="right num">{{ fmt.price(p.liquidationPrice, currency()) }}</td>
                        <td class="right num">{{ fmt.money(p.margin, currency()) }}</td>
                        <td class="right num {{ fmt.trendClass(-p.cumulativeFunding) }}">{{ fmt.signedMoney(-p.cumulativeFunding, currency()) }}</td>
                        <td class="right"><button type="button" class="btn btn-outline small-btn" data-testid="close-position" [disabled]="submitting() || !liveActive()" (click)="closePosition(p)">Close</button></td>
                      </tr>
                    } @empty {
                      <tr><td colspan="10" class="empty">No open positions yet.</td></tr>
                    }
                  </tbody>
                </table>
              }
              @case ('trades') {
                <table class="data">
                  <thead><tr><th>Time</th><th>Market</th><th>Side</th><th>Type</th><th class="right">Size</th><th class="right">Price</th><th class="right">Fee</th><th class="right">Closed PnL</th><th>Status</th></tr></thead>
                  <tbody>
                    @for (o of orders(); track o.orderId) {
                      <tr>
                        <td>{{ o.submittedAt | date: 'MMM d, HH:mm:ss' }}</td>
                        <td class="bold">{{ o.symbol }}</td>
                        <td class="side" [class.buy]="o.side === 'BUY'" [class.sell]="o.side === 'SELL'">{{ o.side === 'BUY' ? 'Buy' : 'Sell' }}</td>
                        <td>{{ o.kind === 'LIQUIDATION' ? 'Liquidation' : 'Market' }}{{ o.reduceOnly && o.kind === 'MARKET' ? ' · reduce only' : '' }}</td>
                        <td class="right num">{{ fmt.quantity(o.filledSize ?? o.requestedSize) }}</td>
                        <td class="right num">{{ fmt.price(o.fillPrice, currency()) }}</td>
                        <td class="right num">{{ fmt.money(o.fee, currency()) }}</td>
                        <td class="right num {{ fmt.trendClass(o.realizedPnl) }}">{{ fmt.signedMoney(o.realizedPnl, currency()) }}</td>
                        <td><span class="badge" [class.filled]="o.status === 'FILLED'" [class.rejected]="o.status === 'REJECTED'" [title]="o.rejectionReason ?? ''">{{ o.status === 'FILLED' ? 'Filled' : 'Rejected' }}</span></td>
                      </tr>
                    } @empty {
                      <tr><td colspan="9" class="empty">No trades yet.</td></tr>
                    }
                  </tbody>
                </table>
              }
              @case ('funding') {
                <table class="data">
                  <thead><tr><th>Time</th><th>Market</th><th>Side</th><th class="right">Size</th><th class="right">Mark</th><th class="right">Rate</th><th class="right">Payment</th></tr></thead>
                  <tbody>
                    @for (f of funding(); track f.fundingId) {
                      <tr>
                        <td>{{ f.fundingTime | date: 'MMM d, HH:mm' }}</td>
                        <td class="bold">{{ f.symbol }}</td>
                        <td class="side" [class.buy]="f.side === 'LONG'" [class.sell]="f.side === 'SHORT'">{{ f.side === 'LONG' ? 'Long' : 'Short' }}</td>
                        <td class="right num">{{ fmt.quantity(f.size) }}</td>
                        <td class="right num">{{ fmt.price(f.markPrice, currency()) }}</td>
                        <td class="right num">{{ rate(f.fundingRate) }}</td>
                        <td class="right num {{ fmt.trendClass(-f.payment) }}">{{ fmt.signedMoney(-f.payment, currency()) }}</td>
                      </tr>
                    } @empty {
                      <tr><td colspan="7" class="empty">No funding payments yet. Positions held at the start of each funding interval ({{ fundingInterval() }}) pay or receive funding.</td></tr>
                    }
                  </tbody>
                </table>
              }
            }
          </div>
        </section>

        <section class="card account">
          <h2>Perpetuals account</h2>
          <dl class="summary">
            <div><dt>Account value</dt><dd class="num" data-testid="account-value">{{ fmt.money(accountValue(), currency()) }}</dd></div>
            <div><dt>Available collateral</dt><dd class="num">{{ fmt.money(account()?.availableCollateral, currency()) }}</dd></div>
            <div><dt>Margin in use</dt><dd class="num">{{ fmt.money(account()?.totalMargin, currency()) }}</dd></div>
            <div><dt>Unrealized PnL</dt><dd class="num {{ fmt.trendClass(unrealizedPnl()) }}" data-testid="unrealized-pnl">{{ fmt.signedMoney(unrealizedPnl(), currency()) }}</dd></div>
            <div><dt>Account leverage</dt><dd class="num">{{ accountLeverage().toFixed(2) }}x</dd></div>
            <div><dt title="What your open positions pay (negative) or receive at the next funding time, at the current rate">Next funding (est.)</dt>
              <dd class="num {{ fmt.trendClass(nextFunding()) }}" data-testid="next-funding">{{ fmt.signedMoney(nextFunding(), currency()) }}</dd></div>
          </dl>
          <p class="hint">Collateral is your cash balance, shared with the Markets page.</p>
        </section>
      </div>
    </main>
  `,
  styles: [`
    .live-controls { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; margin-bottom: 20px; }
    .live-controls .hint { flex-basis: 100%; margin: 0; }
    .wide { max-width: 1440px; }
    .markets { margin-bottom: 16px; }
    .pill-lev { opacity: .7; margin-left: 4px; }
    .pill.small { padding: 5px 12px; min-height: 30px; font-size: 12px; }
    .market-bar { display: flex; align-items: center; gap: 16px 40px; flex-wrap: wrap; }
    .eyebrow { font-size: 12px; font-weight: 600; color: var(--text-muted); text-transform: uppercase; letter-spacing: .04em; margin: 0 0 6px; }
    .market-bar h2 { font-size: 24px; }
    .stats { display: flex; flex-wrap: wrap; gap: 12px 32px; margin: 0; }
    .stats dt { font-size: 12px; color: var(--text-muted); margin-bottom: 4px; }
    .stats dd { margin: 0; font-size: 15px; font-weight: 600; }
    .bar-alert { margin: 0; flex-basis: 100%; }
    .grid { display: grid; gap: 24px; grid-template-columns: minmax(0, 1fr) 260px 320px; align-items: start; }
    .grid.bottom { grid-template-columns: minmax(0, 1fr) 320px; }
    .grid > .card { margin-bottom: 0; min-width: 0; }
    .grid + .grid { margin-top: 24px; }
    .oi-split { display: block; font-size: 11px; font-weight: 500; margin-top: 2px; }
    .chart-card .hint { margin: 10px 0 0; }
    .book { padding: 16px 0 12px; }
    .book .tabs, .book-note, .book .skeleton { padding: 0 16px; }
    .book-note { margin: 10px 0 8px; font-size: 12px; }
    .level { position: relative; display: grid; grid-template-columns: repeat(3, 1fr); padding: 3px 16px; font-size: 12px; text-align: right; }
    .level span:first-of-type { text-align: left; }
    .level.head { color: var(--text-muted); font-weight: 600; }
    .depth { position: absolute; top: 1px; bottom: 1px; right: 0; }
    .ask .depth { background: var(--red-soft); } .bid .depth { background: var(--green-soft); }
    .level span { position: relative; }
    .spread { background: var(--surface-alt); color: var(--text-muted); margin: 2px 0; }
    .sides { display: flex; gap: 8px; margin-bottom: 18px; }
    .side-btn { flex: 1; padding: 10px 0; min-height: 44px; border-radius: 8px; font-weight: 700; font-size: 14px; border: none; cursor: pointer; background: var(--surface-alt); color: var(--text-muted); }
    .side-btn.buy.active { background: var(--green-soft); color: var(--green); }
    .side-btn.sell.active { background: var(--red-soft); color: var(--red); }
    .field-label { display: block; font-size: 13px; font-weight: 600; color: var(--text-muted); margin-bottom: 6px; }
    .leverage { display: flex; justify-content: space-between; align-items: baseline; }
    input[type=range] { width: 100%; accent-color: var(--brand); margin: 0 0 6px; }
    .row { display: flex; justify-content: space-between; gap: 12px; font-size: 14px; padding: 10px 0; border-top: 1px solid var(--border); }
    .row + .field-label { margin-top: 6px; }
    .quick { display: flex; gap: 6px; margin: 10px 0 12px; }
    .quick-btn { flex: 1; padding: 6px 0; min-height: 32px; border-radius: 999px; font-size: 12px; font-weight: 600; border: 1px solid var(--border); background: transparent; color: var(--text-muted); cursor: pointer; }
    .quick-btn:hover:not(:disabled) { color: var(--text); border-color: var(--text-muted); }
    .check { display: flex; align-items: center; gap: 8px; font-size: 14px; color: var(--text-muted); margin-bottom: 16px; }
    .submit { background: var(--green); color: #fff; }
    .submit.sell { background: var(--red); }
    .summary { margin: 18px 0 0; display: flex; flex-direction: column; gap: 10px; font-size: 14px; }
    .summary div { display: flex; justify-content: space-between; gap: 12px; }
    .summary dt { color: var(--text-muted); }
    .summary dd { margin: 0; font-weight: 600; text-align: right; }
    .account h2 { margin-bottom: 4px; }
    .activity td { white-space: nowrap; }
    .sub-num { margin-left: 4px; font-size: 12px; }
    .small-btn { min-height: 32px; padding: 6px 12px; font-size: 13px; }
    @media (max-width: 1200px) { .grid { grid-template-columns: minmax(0, 1fr) 320px; } .book { grid-column: 1; } .ticket { grid-row: 1 / span 2; grid-column: 2; } }
    @media (max-width: 860px) { .grid, .grid.bottom { grid-template-columns: minmax(0, 1fr); } .ticket { grid-row: auto; grid-column: auto; order: -1; } }
  `],
})
export class PerpsComponent implements OnInit, OnDestroy {
  private readonly perps = inject(PerpsApi);
  private readonly stream = inject(PerpsStream);
  private readonly pollMs = inject(PERPS_POLL_MS);
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));
  private readonly subscriptions = new Subscription();
  private liveSubscriptions = new Subscription();
  private liveUntil = 0;
  readonly liveActive = signal(false);
  readonly liveRemaining = computed(() => {
    const seconds = Math.max(0, Math.ceil((this.liveUntil - this.now()) / 1000));
    return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
  });
  private readonly refresh$ = new Subject<void>();
  private readonly candleReload$ = new Subject<void>();
  // Reused only when retrying the same order after an uncertain failure, so a retry can't fill twice
  private pendingOrder: { payload: string; key: string } | null = null;

  protected readonly fmt = { money, price, quantity, signedMoney, trendClass };
  protected readonly quickAmounts = QUICK_AMOUNTS;
  protected readonly candleOptions = [
    { label: '1m', seconds: 60 },
    { label: '5m', seconds: 300 },
    { label: '15m', seconds: 900 },
    { label: '1h', seconds: 3600 },
  ];

  readonly markets = signal<PerpMarket[]>([]);
  readonly selectedSymbol = signal('BTC-PERP');
  // Latest streamed ticker per market symbol
  readonly tickers = signal<Record<string, PerpTicker>>({});
  readonly account = signal<PerpAccount | null>(null);
  readonly orders = signal<PerpOrder[]>([]);
  readonly funding = signal<PerpFunding[]>([]);
  readonly candles = signal<Candle[]>([]);
  readonly liveCandle = signal<Candle | null>(null);
  readonly candlesError = signal('');
  readonly now = signal(Date.now());
  readonly tickerError = signal('');

  readonly side = signal<OrderSide>('BUY');
  readonly sizeInput = signal('');
  readonly chosenLeverage = signal(10);
  readonly reduceOnly = signal(false);
  readonly submitting = signal(false);
  readonly orderError = signal('');
  readonly orderSuccess = signal('');
  readonly bookTab = signal<'book' | 'fills'>('book');
  readonly bottomTab = signal<'positions' | 'trades' | 'funding'>('positions');
  readonly candleSeconds = signal(60);

  readonly ticker = computed(() => this.tickers()[this.selectedSymbol()] ?? null);
  readonly market = computed(() => this.markets().find((m) => m.symbol === this.selectedSymbol()) ?? null);
  readonly currency = computed(() => this.account()?.currency ?? this.market()?.quoteCurrency ?? 'USD');
  readonly baseAsset = computed(() => this.market()?.baseAsset ?? this.selectedSymbol().replace(/-PERP$/, ''));
  readonly maxLeverage = computed(() => this.market()?.maxLeverage ?? this.ticker()?.maxLeverage ?? 1);
  // Server positions re-marked at the streamed mark, so value and PnL move with every tick
  readonly positions = computed<PerpPosition[]>(() => {
    const tickers = this.tickers();
    return (this.account()?.positions ?? []).map((p) => {
      const mark = tickers[p.symbol]?.markPrice;
      return mark ? { ...p, ...markToMarket(p, mark) } : p;
    });
  });
  readonly position = computed(() => this.positions().find((p) => p.symbol === this.selectedSymbol()) ?? null);
  readonly unrealizedPnl = computed(() => {
    const account = this.account();
    if (!account) return null;
    return this.positions().reduce((sum, p) => sum + (p.unrealizedPnl ?? 0), 0);
  });
  readonly accountValue = computed(() => {
    const account = this.account();
    return account ? account.availableCollateral + account.totalMargin + (this.unrealizedPnl() ?? 0) : null;
  });
  // Positive when the account receives funding at the next interval
  readonly nextFunding = computed(() => {
    const account = this.account();
    if (!account) return null;
    const tickers = this.tickers();
    return this.positions().reduce((sum, p) => {
      const t = tickers[p.symbol];
      return t ? sum - (p.side === 'LONG' ? 1 : -1) * p.size * t.markPrice * t.fundingRate : sum;
    }, 0);
  });
  readonly fundingInterval = computed(() => {
    const minutes = this.ticker()?.fundingIntervalMinutes ?? 60;
    return minutes % 60 === 0 ? `${minutes / 60}h` : `${minutes}m`;
  });
  // An open position fixes the leverage: the backend rejects adds at a different leverage
  readonly leverage = computed(() => this.position()?.leverage ?? Math.min(this.chosenLeverage(), this.maxLeverage()));
  readonly size = computed(() => {
    const value = Number(this.sizeInput());
    return Number.isFinite(value) && value > 0 ? value : 0;
  });
  readonly fillPrice = computed(() => this.estimateFill(this.size()));
  readonly preview = computed(() => {
    const ticker = this.ticker();
    if (!ticker || this.size() <= 0) return null;
    const position = this.position();
    return previewOrder({
      side: this.side(),
      size: this.size(),
      price: this.fillPrice(),
      leverage: this.leverage(),
      takerFeeRate: ticker.takerFeeRate,
      maintenanceMarginRate: ticker.maintenanceMarginRate,
      reduceOnly: this.reduceOnly(),
      position: position ? { side: position.side, size: position.size, entryPrice: position.entryPrice, margin: position.margin } : null,
    });
  });
  readonly maxSize = computed(() => {
    const available = this.account()?.availableCollateral ?? 0;
    const feeRate = this.ticker()?.takerFeeRate ?? 0;
    // A bigger order pays a bigger premium, so size against the price the touch-price maximum would fill at;
    // that size fills no higher, so its margin and fee still fit
    const atTouch = maxOrderSize(available, this.estimateFill(0), this.leverage(), feeRate);
    return maxOrderSize(available, this.estimateFill(atTouch), this.leverage(), feeRate);
  });
  readonly ladder = computed(() => {
    const ticker = this.ticker();
    return ticker ? buildDepthLadder(ticker.bid, ticker.ask, 9) : null;
  });
  readonly maxDepth = computed(() => {
    const ladder = this.ladder();
    return ladder ? Math.max(ladder.asks[0]?.total ?? 0, ladder.bids[ladder.bids.length - 1]?.total ?? 0) : 1;
  });
  readonly countdown = computed(() => {
    const ticker = this.ticker();
    return ticker ? formatCountdown(Date.parse(ticker.nextFundingAt) - this.now()) : '--:--:--';
  });
  readonly fundingClass = computed(() => trendClass(this.ticker()?.fundingRate));
  readonly chartLines = computed<ChartLine[]>(() => {
    const lines: ChartLine[] = [];
    const ticker = this.ticker();
    const position = this.position();
    if (ticker) lines.push({ price: ticker.markPrice, title: 'Mark', tone: 'brand', dashed: true });
    if (position) {
      lines.push({ price: position.entryPrice, title: 'Entry', tone: 'muted' });
      if (position.liquidationPrice !== null) lines.push({ price: position.liquidationPrice, title: 'Liq.', tone: 'down', dashed: true });
    }
    return lines;
  });
  readonly accountLeverage = computed(() => {
    const value = this.accountValue();
    if (!value || value <= 0) return 0;
    const notional = this.positions().reduce((sum, p) => sum + (p.positionValue ?? p.size * p.entryPrice), 0);
    return notional / value;
  });
  readonly fills = computed(() => this.orders().filter((o) => o.status === 'FILLED'));
  readonly submitLabel = computed(() => (this.side() === 'BUY' ? 'Buy / Long' : 'Sell / Short'));
  readonly positionText = computed(() => {
    const position = this.position();
    if (!position) return `0 ${this.baseAsset()}`;
    return `${position.side === 'LONG' ? 'Long' : 'Short'} ${quantity(position.size)} ${this.baseAsset()}`;
  });

  ngOnInit(): void {
    this.subscriptions.add(this.perps.markets().subscribe({
      next: (markets) => {
        this.markets.set(markets ?? []);
        if (markets?.length && !markets.some((m) => m.symbol === this.selectedSymbol())) {
          this.selectMarket(markets[0].symbol);
        }
      },
      error: (error) => this.tickerError.set(errorMessage(error, 'Perpetual markets could not be loaded.')),
    }));

    // History does not use the live price feed, so the chart is shown while dormant too
    // switchMap drops history for a market or interval the user has already left
    this.subscriptions.add(this.candleReload$.pipe(
      switchMap(() => this.perps.candles(this.selectedSymbol(), this.candleSeconds()).pipe(catchError((error) => {
        this.candlesError.set(errorMessage(error, 'Price history is unavailable right now.'));
        return EMPTY;
      }))),
    ).subscribe((candles) => {
      this.candles.set(candles ?? []);
      this.liveCandle.set(null);
      this.candlesError.set('');
    }));
    if (this.isBrowser) {
      this.candleReload$.next();
      this.loadAccountOnce();
    }
  }

  startLiveUpdates(): void {
    if (!this.isBrowser || this.liveActive()) return;
    this.liveSubscriptions = new Subscription();
    this.liveUntil = Date.now() + LIVE_SESSION_MS;
    this.now.set(Date.now());
    this.liveActive.set(true);
    const polling = this.isBrowser && this.pollMs > 0;
    const ticks = polling ? merge(this.refresh$, interval(this.pollMs)) : this.refresh$;
    const refreshes = ticks.pipe(switchMap(() => {
      if (Date.now() >= this.liveUntil) {
        this.stopLiveUpdates();
        return EMPTY;
      }
      return [undefined];
    }));
    this.liveSubscriptions.add(this.latest(refreshes, () => this.perps.account()).subscribe((account) => this.account.set(account)));
    this.liveSubscriptions.add(this.latest(refreshes, () => this.perps.orders()).subscribe((orders) => this.orders.set(orders ?? [])));
    this.liveSubscriptions.add(this.latest(refreshes, () => this.perps.funding()).subscribe((funding) => this.funding.set(funding ?? [])));
    this.liveSubscriptions.add(this.stream.tickers().pipe(
      retry({
        resetOnSuccess: true,
        delay: (error, attempt) => {
          // An expired session is handled by the polled requests, which send the user to sign in
          if (error instanceof PerpStreamError && (error.status === 401 || error.status === 403)) return throwError(() => error);
          this.tickerError.set('The live price stream was interrupted. Reconnecting…');
          return timer(Math.min(MAX_STREAM_RETRY_MS, 1000 * 2 ** (attempt - 1)));
        },
      }),
    ).subscribe({
      next: (tickers) => this.onTickers(tickers),
      error: () => this.tickerError.set('The live price stream is unavailable. Sign in again to resume.'),
    }));
    if (polling) {
      this.liveSubscriptions.add(interval(1000).subscribe(() => this.now.set(Date.now())));
    }
    this.liveSubscriptions.add(timer(LIVE_SESSION_MS).subscribe(() => this.stopLiveUpdates()));
    // Catch up on candles missed while dormant before live ticks extend them
    this.candleReload$.next();
    this.refresh();
  }

  stopLiveUpdates(): void {
    this.liveSubscriptions.unsubscribe();
    this.liveActive.set(false);
  }

  ngOnDestroy(): void {
    this.stopLiveUpdates();
    this.subscriptions.unsubscribe();
  }

  refresh(): void {
    if (!this.liveActive()) return;
    if (Date.now() >= this.liveUntil) {
      this.stopLiveUpdates();
      return;
    }
    this.refresh$.next();
  }

  selectMarket(symbol: string): void {
    if (symbol === this.selectedSymbol()) return;
    this.selectedSymbol.set(symbol);
    this.sizeInput.set('');
    this.orderError.set('');
    this.candles.set([]);
    this.liveCandle.set(null);
    this.candleReload$.next();
  }

  setCandleInterval(seconds: number): void {
    if (seconds === this.candleSeconds()) return;
    this.candleSeconds.set(seconds);
    this.candles.set([]);
    this.liveCandle.set(null);
    this.candleReload$.next();
  }

  setSide(side: OrderSide): void {
    this.side.set(side);
  }

  setSize(value: string): void {
    this.sizeInput.set(value);
  }

  setSizePercent(percent: number): void {
    // Round down so the ticket never asks for more than the collateral covers
    const size = Math.floor(this.maxSize() * percent / 100 * 1e6) / 1e6;
    this.sizeInput.set(size > 0 ? String(size) : '');
  }

  setLeverage(value: number): void {
    this.chosenLeverage.set(Math.max(1, Math.min(this.maxLeverage(), Math.round(value))));
  }

  submit(): void {
    if (this.size() <= 0) {
      this.orderError.set(`Enter a size in ${this.baseAsset()}.`);
      return;
    }
    this.placeOrder({
      symbol: this.selectedSymbol(),
      side: this.side(),
      size: this.size(),
      leverage: this.leverage(),
      reduceOnly: this.reduceOnly(),
    }, () => this.sizeInput.set(''));
  }

  closePosition(position: PerpPosition): void {
    this.placeOrder({
      symbol: position.symbol,
      side: position.side === 'LONG' ? 'SELL' : 'BUY',
      size: position.size,
      leverage: position.leverage,
      reduceOnly: true,
    });
  }

  protected rate(value: number | null | undefined): string {
    if (value === null || value === undefined) return '—';
    return `${(value * 100).toFixed(4)}%`;
  }

  protected signedRate(value: number | null | undefined): string {
    if (value === null || value === undefined) return '—';
    return `${value > 0 ? '+' : value < 0 ? '−' : ''}${Math.abs(value * 100).toFixed(3)}%`;
  }

  protected roe(value: number): string {
    return `${value > 0 ? '+' : value < 0 ? '−' : ''}${Math.abs(value * 100).toFixed(2)}%`;
  }

  /** Book prices without the currency symbol, at the ladder's tick precision. */
  protected bookPrice(value: number | null | undefined): string {
    if (value === null || value === undefined) return '—';
    const tick = this.ladder()?.tick ?? 0.01;
    const digits = Math.min(6, Math.max(2, -Math.floor(Math.log10(tick))));
    return value.toLocaleString('en-US', { minimumFractionDigits: digits, maximumFractionDigits: digits });
  }

  private placeOrder(request: PerpOrderRequest, onFilled?: () => void): void {
    if (!this.liveActive() || Date.now() >= this.liveUntil) {
      this.stopLiveUpdates();
      return;
    }
    const payload = JSON.stringify(request);
    if (this.pendingOrder?.payload !== payload) {
      this.pendingOrder = { payload, key: newIdempotencyKey() };
    }
    this.submitting.set(true);
    this.orderError.set('');
    this.orderSuccess.set('');

    this.subscriptions.add(this.perps.submit(request, this.pendingOrder.key)
      .pipe(finalize(() => this.submitting.set(false)))
      .subscribe({
        next: (order) => {
          this.pendingOrder = null;
          this.orderSuccess.set(order?.fillPrice
            ? `Filled ${quantity(order.filledSize)} ${request.symbol} at ${price(order.fillPrice, this.currency())}`
            : 'Order filled');
          onFilled?.();
          this.refresh();
        },
        error: (error) => {
          // Network errors and 5xx may have filled server-side; keep the key so a retry is deduplicated
          if (error.status !== 0 && error.status < 500) {
            this.pendingOrder = null;
          }
          this.orderError.set(errorMessage(error, 'The order could not be placed. Please try again.'));
          // Rejected orders are recorded too, so show them in history
          this.refresh();
        },
      }));
  }

  // Positions, trades and funding history are shown on arrival; live updates then keep them current.
  // A live session's own responses are newer, so this one-off load never overwrites them.
  private loadAccountOnce(): void {
    const once = <T>(load: Observable<T>, apply: (value: T) => void) => this.subscriptions.add(
      load.pipe(catchError(() => EMPTY)).subscribe((value) => { if (!this.liveActive()) apply(value); }));
    once(this.perps.account(), (account) => this.account.set(account));
    once(this.perps.orders(), (orders) => this.orders.set(orders ?? []));
    once(this.perps.funding(), (funding) => this.funding.set(funding ?? []));
  }

  // switchMap drops an in-flight request when a newer refresh starts, so a slow older response can't overwrite newer data
  private latest<T>(trigger: Observable<unknown>, load: () => Observable<T>): Observable<T> {
    return trigger.pipe(switchMap(() => load().pipe(catchError(() => EMPTY))));
  }

  private onTickers(tickers: PerpTicker[]): void {
    if (!tickers?.length) return;
    this.tickers.update((current) => {
      const next = { ...current };
      for (const ticker of tickers) next[ticker.symbol] = ticker;
      return next;
    });
    this.tickerError.set('');
    const selected = tickers.find((t) => t.symbol === this.selectedSymbol());
    if (!selected) return;
    const history = this.candles();
    const last = this.liveCandle() ?? history[history.length - 1] ?? null;
    const next = mergeTick(last, Date.parse(selected.capturedAt) / 1000, selected.oraclePrice, this.candleSeconds());
    if (next) this.liveCandle.set(next);
  }

  /** Expected fill for this ticket's side at a given size, including the premium the order pays. */
  private estimateFill(size: number): number {
    const ticker = this.ticker();
    if (!ticker) return 0;
    const market = this.market();
    const position = this.position();
    // Reduce-only orders against the position fill at most its size
    const closing = this.reduceOnly() && position && (position.side === 'LONG') !== (this.side() === 'BUY');
    return estimateFillPrice({
      side: this.side(),
      size: closing ? Math.min(size, position!.size) : size,
      bid: ticker.bid,
      ask: ticker.ask,
      premium: ticker.premium,
      skew: ticker.longOpenInterest - ticker.shortOpenInterest,
      skewScale: market?.skewScale ?? 0,
      maxPremium: market?.maxPremium ?? 0,
    });
  }
}
