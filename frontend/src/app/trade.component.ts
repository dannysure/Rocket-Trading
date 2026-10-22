import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { catchError, of } from 'rxjs';
import { Candle, Instrument, MarketApi, NewsApi, NewsArticle, Quote, Segment, WatchlistApi, errorMessage } from './api.service';
import { NavHeaderComponent } from './nav-header.component';
import { TradeTicketComponent } from './trade-ticket.component';
import { InstrumentSearchComponent } from './instrument-search.component';
import { SEGMENTS, displaySymbol, percent, price, segmentLabel, sparkPath, trendClass } from './format';

const RANGES = [
  { days: 30, label: '1M' },
  { days: 90, label: '3M' },
  { days: 365, label: '1Y' },
];

@Component({
  selector: 'app-trade',
  imports: [DatePipe, RouterLink, NavHeaderComponent, TradeTicketComponent, InstrumentSearchComponent],
  template: `
    <app-nav-header />
    <main class="page">
      <h1 class="page-title">Markets</h1>
      <p class="page-subtitle">US, UK and Indian shares, currencies and crypto, all settled in your account currency.</p>

      <app-instrument-search class="market-search" [instruments]="instruments()"
                             (picked)="pick($event)" (catalogueChanged)="instruments.set($event)" />

      <div class="pill-row segments" role="tablist" aria-label="Markets">
        @for (s of segments; track s.key) {
          <button type="button" role="tab" class="pill" [attr.aria-selected]="segment() === s.key" [class.active]="segment() === s.key" (click)="selectSegment(s.key)">{{ s.label }}</button>
        }
      </div>
      @if (segment() === 'UK') {
        <p class="hint uk-note">UK companies trade here through their US listings, priced in US dollars.</p>
      }

      <div class="chips" aria-label="Instruments">
        @for (i of segmentInstruments(); track i.symbol) {
          <button type="button" class="chip" [class.active]="selected()?.symbol === i.symbol" (click)="select(i)">
            <span class="bold">{{ fmt.displaySymbol(i.symbol) }}</span><span class="muted chip-name">{{ i.name }}</span>
          </button>
        }
      </div>
      @if (loadError()) { <p class="alert error" role="alert">{{ loadError() }}</p> }

      @if (selected(); as instrument) {
        <div class="layout">
          <section class="card main">
            <div class="head">
              <div>
                <p class="eyebrow">{{ fmt.segmentLabel(instrument.segment) }}@if (instrument.exchange) { · {{ instrument.exchange }} }</p>
                <h2>{{ fmt.displaySymbol(instrument.symbol) }} <span class="muted name">{{ instrument.name }}</span></h2>
                <button type="button" class="star" (click)="toggleWatch(instrument)" [attr.aria-pressed]="watched()">
                  <svg width="16" height="16" viewBox="0 0 24 24" aria-hidden="true"><path [attr.fill]="watched() ? 'currentColor' : 'none'" stroke="currentColor" stroke-width="2" stroke-linejoin="round" d="M12 3l2.8 5.7 6.2.9-4.5 4.4 1.1 6.2L12 17.3 6.4 20.2l1.1-6.2L3 9.6l6.2-.9z"/></svg>
                  {{ watched() ? 'On your watchlist' : 'Add to watchlist' }}
                </button>
              </div>
              <div class="price-block">
                @if (quote(); as q) {
                  <p class="num big">{{ fmt.price(q.price, q.currency ?? instrument.baseCurrency) }}</p>
                  <p class="num {{ fmt.trendClass(q.changePercent) }} chg">{{ fmt.percent(q.changePercent) }} today</p>
                  @if (!q.current) { <span class="badge waiting">Market closed · last price</span> }
                } @else if (quoteError()) {
                  <p class="hint">{{ quoteError() }}</p>
                } @else {
                  <p class="skeleton">Fetching price…</p>
                }
              </div>
            </div>

            <div class="pill-row ranges">
              @for (r of ranges; track r.days) {
                <button type="button" class="pill small" [class.active]="rangeDays() === r.days" (click)="rangeDays.set(r.days)">{{ r.label }}</button>
              }
            </div>
            @if (chart().line && history(); as h) {
              <p class="chart-caption">
                Daily closing prices · {{ h.from }} – {{ h.to }}
                <span class="num {{ fmt.trendClass(h.change) }}">{{ fmt.percent(h.change) }}</span>
              </p>
              <div class="chart-wrap">
                <svg viewBox="0 0 1000 200" preserveAspectRatio="none" class="chart" role="img" [attr.aria-label]="chartLabel()">
                  <defs>
                    <linearGradient id="chartFill" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stop-color="#1F49E6" stop-opacity="0.16" />
                      <stop offset="100%" stop-color="#1F49E6" stop-opacity="0" />
                    </linearGradient>
                  </defs>
                  <path [attr.d]="chart().area" fill="url(#chartFill)" />
                  <path [attr.d]="chart().line" fill="none" stroke="#1F49E6" stroke-width="2.5" stroke-linejoin="round" vector-effect="non-scaling-stroke" />
                </svg>
                <span class="axis top num" aria-hidden="true">{{ fmt.price(h.max, ccy()) }}</span>
                <span class="axis bottom num" aria-hidden="true">{{ fmt.price(h.min, ccy()) }}</span>
              </div>
              <div class="chart-dates" aria-hidden="true"><span>{{ h.from }}</span><span>{{ h.to }}</span></div>
              @if (h.short) {
                <p class="hint">Fauxnance only has history from {{ h.from }} for this instrument.</p>
              }
              <p class="hint source">Source: Fauxnance. Days without trading (weekends, holidays) and estimated days are left out. The live price above may be newer than the last close.</p>
            } @else {
              <p class="chart-empty">{{ historyLoading() ? 'Loading price history…' : 'Fauxnance has no price history for this instrument.' }}</p>
            }

            <dl class="stats">
              <div><dt>Bid</dt><dd class="num">{{ fmt.price(quote()?.bid, ccy()) }}</dd></div>
              <div><dt>Ask</dt><dd class="num">{{ fmt.price(quote()?.ask, ccy()) }}</dd></div>
              <div><dt>Previous close</dt><dd class="num">{{ fmt.price(quote()?.previousClose, ccy()) }}</dd></div>
              <div><dt>{{ rangeLabel() }} high</dt><dd class="num">{{ fmt.price(rangeHigh(), ccy()) }}</dd></div>
              <div><dt>{{ rangeLabel() }} low</dt><dd class="num">{{ fmt.price(rangeLow(), ccy()) }}</dd></div>
              <div><dt>Price time</dt><dd>{{ quoteTime() }}</dd></div>
            </dl>
          </section>

          <section class="card ticket" aria-label="Order ticket">
            <app-trade-ticket [instrument]="instrument" />
          </section>
        </div>
        <section class="card stock-news" aria-labelledby="stock-news-title">
          <div class="card-head">
            <div><p class="eyebrow">Market intelligence</p><h2 id="stock-news-title">{{ fmt.displaySymbol(instrument.symbol) }} news</h2></div>
            <a routerLink="/news" class="link">Broader market news →</a>
          </div>
          @if (newsLoading()) {
            <p class="skeleton">Loading stories…</p>
          } @else if (news().length) {
            <div class="stock-news-list">
              @for (article of news().slice(0, 5); track article.id) {
                <a class="stock-news-item" [href]="article.url" target="_blank" rel="noopener noreferrer">
                  <span class="news-meta">{{ article.source }} · {{ article.publishedAt | date: 'MMM d, y' }}</span>
                  <strong>{{ article.headline }}</strong><span class="arrow" aria-hidden="true">↗</span>
                </a>
              }
            </div>
          } @else {
            <p class="empty">No recent stories found for this instrument.</p>
          }
        </section>
      }
    </main>
  `,
  styles: [`
    .market-search { max-width: 520px; margin-bottom: 20px; }
    .segments { margin-bottom: 12px; }
    .uk-note { margin: 0 0 12px; }
    .chips { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 24px; }
    .chip { border: 1px solid var(--border); background: var(--surface); border-radius: 10px; padding: 8px 12px; min-height: 40px; font-size: 13px; cursor: pointer; color: var(--text); }
    .chip:hover { border-color: var(--text-muted); }
    .chip-name { margin-left: 6px; }
    .chip.active { border-color: var(--brand); box-shadow: inset 0 0 0 1px var(--brand); }
    .layout { display: flex; gap: 32px; flex-wrap: wrap; align-items: flex-start; }
    .main { flex: 999 1 600px; min-width: 0; padding: 28px; }
    .ticket { flex: 1 1 340px; min-width: min(300px, 100%); }
    .head { display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 16px; }
    .eyebrow { font-size: 12px; font-weight: 600; color: var(--text-muted); text-transform: uppercase; letter-spacing: .04em; margin: 0 0 6px; }
    h2 { font-size: 24px; }
    .name { font-weight: 400; font-size: 16px; font-family: var(--font-body); }
    .star { display: inline-flex; align-items: center; gap: 6px; margin-top: 10px; background: none; border: 1px solid var(--border); border-radius: 999px; padding: 6px 12px; min-height: 32px; font-size: 13px; font-weight: 600; color: var(--amber-text); cursor: pointer; }
    .price-block { text-align: right; }
    .big { font-size: 28px; font-weight: 700; font-family: var(--font-display); margin: 0; }
    .chg { margin: 4px 0 8px; font-size: 14px; font-weight: 600; }
    .ranges { margin: 20px 0 8px; }
    .pill.small { padding: 5px 12px; min-height: 30px; font-size: 12px; }
    .chart { width: 100%; height: 200px; display: block; }
    .chart-caption { font-size: 13px; color: var(--text-muted); margin: 0 0 8px; display: flex; gap: 8px; flex-wrap: wrap; }
    .chart-caption .num { font-weight: 600; }
    .chart-wrap { position: relative; }
    .axis { position: absolute; right: 0; font-size: 11px; color: var(--text-muted); background: rgba(255,255,255,.85); padding: 1px 4px; border-radius: 4px; }
    .axis.top { top: 0; }
    .axis.bottom { bottom: 0; }
    .chart-dates { display: flex; justify-content: space-between; font-size: 11px; color: var(--text-muted); margin-top: 4px; }
    .source { font-size: 12px; }
    .chart-empty { height: 200px; display: flex; align-items: center; justify-content: center; color: var(--text-muted); font-size: 14px; background: var(--bg); border-radius: 12px; margin: 0; }
    .stats { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(140px, 100%), 1fr)); gap: 16px; margin: 24px 0 0; padding-top: 20px; border-top: 1px solid var(--border); }
    .stats dt { font-size: 12px; color: var(--text-muted); margin-bottom: 4px; }
    .stats dd { margin: 0; font-size: 15px; font-weight: 600; }
    .stock-news { margin-top: 24px; }
    .stock-news .card-head { align-items: flex-start; }
    .stock-news-list { display: flex; flex-direction: column; }
    .stock-news-item { display: grid; grid-template-columns: 150px minmax(0, 1fr) auto; gap: 12px; align-items: center; padding: 13px 0; border-top: 1px solid var(--border); }
    .stock-news-item strong { font-size: 14px; line-height: 1.35; }
    .news-meta { color: var(--text-muted); font-size: 12px; }
    .arrow { color: var(--brand); font-size: 18px; }
    @media (max-width: 640px) { .stock-news-item { grid-template-columns: 1fr auto; } .stock-news-item .news-meta { grid-column: 1 / -1; } }
  `],
})
export class TradeComponent implements OnInit {
  /** Preselects an instrument from ?symbol= (e.g. a Trade link elsewhere). */
  readonly symbol = input<string>();

  private readonly market = inject(MarketApi);
  private readonly newsApi = inject(NewsApi);
  private readonly watchlistApi = inject(WatchlistApi);
  private readonly router = inject(Router);

  protected readonly fmt = { price, percent, trendClass, segmentLabel, displaySymbol };
  protected readonly segments = SEGMENTS;
  protected readonly ranges = RANGES;

  protected readonly instruments = signal<Instrument[]>([]);
  protected readonly segment = signal<Segment>('US');
  protected readonly selected = signal<Instrument | null>(null);
  protected readonly quote = signal<Quote | null>(null);
  protected readonly quoteError = signal('');
  protected readonly candles = signal<Candle[]>([]);
  protected readonly historyLoading = signal(false);
  protected readonly rangeDays = signal(30);
  protected readonly watchedSymbols = signal<Set<string>>(new Set());
  protected readonly loadError = signal('');
  protected readonly news = signal<NewsArticle[]>([]);
  protected readonly newsLoading = signal(false);

  protected readonly segmentInstruments = computed(() => this.instruments().filter((i) => i.segment === this.segment()));
  protected readonly watched = computed(() => this.watchedSymbols().has(this.selected()?.symbol ?? ''));
  protected readonly ccy = computed(() => this.quote()?.currency ?? this.selected()?.baseCurrency ?? 'USD');
  protected readonly chart = computed(() => sparkPath(this.candles().map((c) => Number(c.close)), 1000, 200));
  protected readonly rangeLabel = computed(() => RANGES.find((r) => r.days === this.rangeDays())?.label ?? '');
  protected readonly rangeHigh = computed(() => this.extreme((c) => c.high ?? c.close, Math.max));
  protected readonly rangeLow = computed(() => this.extreme((c) => c.low ?? c.close, Math.min));
  protected readonly quoteTime = computed(() => {
    const q = this.quote();
    return q ? new Date(q.capturedAt).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' }) : '—';
  });
  /** What the chart covers, so it is never read as more than it is. */
  protected readonly history = computed(() => {
    const c = this.candles();
    if (c.length < 2) return null;
    const closes = c.map((x) => Number(x.close));
    const first = closes[0];
    const last = closes[closes.length - 1];
    const fmtDate = (d: string) => new Date(d + 'T00:00:00Z').toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC' });
    const requestedFrom = Date.now() - this.rangeDays() * 86_400_000;
    return {
      from: fmtDate(c[0].date),
      to: fmtDate(c[c.length - 1].date),
      min: Math.min(...closes),
      max: Math.max(...closes),
      change: first ? ((last - first) / first) * 100 : null,
      // A week of slack covers weekends and holidays at the start of the range
      short: new Date(c[0].date + 'T00:00:00Z').getTime() - requestedFrom > 7 * 86_400_000,
    };
  });

  protected readonly chartLabel = computed(() => {
    const c = this.candles();
    if (c.length < 2) return 'Price history';
    return `${displaySymbol(this.selected()?.symbol)} closing prices from ${c[0].date} to ${c[c.length - 1].date}, ` +
      `from ${price(c[0].close, this.ccy())} to ${price(c[c.length - 1].close, this.ccy())}`;
  });

  constructor() {
    // History follows the selected instrument and range
    effect(() => {
      const instrument = this.selected();
      const days = this.rangeDays();
      if (!instrument) return;
      untracked(() => this.loadHistory(instrument.symbol, days));
    });
  }

  ngOnInit(): void {
    this.market.instruments().subscribe({
      next: (list) => {
        this.instruments.set(list);
        const wanted = list.find((i) => i.symbol === this.symbol()) ?? list.find((i) => i.segment === 'US') ?? list[0];
        if (wanted) {
          this.segment.set(wanted.segment);
          this.select(wanted);
        }
      },
      error: (e) => this.loadError.set(errorMessage(e, 'The instrument list could not be loaded.')),
    });
    this.watchlistApi.list().subscribe({
      next: (items) => this.watchedSymbols.set(new Set(items.map((w) => w.instrument.symbol))),
      error: () => undefined,
    });
  }

  selectSegment(segment: Segment): void {
    this.segment.set(segment);
    const first = this.segmentInstruments()[0];
    if (first) this.select(first);
  }

  /** From search: jump to the instrument's market tab as well as the instrument. */
  pick(instrument: Instrument): void {
    this.segment.set(instrument.segment);
    this.select(instrument);
  }

  select(instrument: Instrument): void {
    this.selected.set(instrument);
    this.router.navigate([], { queryParams: { symbol: instrument.symbol }, replaceUrl: true });
    this.quote.set(null);
    this.quoteError.set('');
    this.news.set([]);
    this.newsLoading.set(true);
    this.market.quote(instrument.symbol).subscribe({
      next: (q) => { if (this.selected()?.symbol === instrument.symbol) this.quote.set(q); },
      error: (e) => this.quoteError.set(errorMessage(e, 'No price available right now.')),
    });
    this.newsApi.forSymbol(instrument.symbol).pipe(catchError(() => of([] as NewsArticle[]))).subscribe((articles) => {
      if (this.selected()?.symbol !== instrument.symbol) return;
      this.news.set(articles);
      this.newsLoading.set(false);
    });
  }

  toggleWatch(instrument: Instrument): void {
    const request = this.watched() ? this.watchlistApi.remove(instrument.symbol) : this.watchlistApi.add(instrument.symbol);
    request.subscribe({
      next: (items) => this.watchedSymbols.set(new Set(items.map((w) => w.instrument.symbol))),
      error: (e) => this.loadError.set(errorMessage(e, 'Your watchlist could not be updated.')),
    });
  }

  private loadHistory(symbol: string, days: number): void {
    this.historyLoading.set(true);
    this.candles.set([]);
    this.market.history(symbol, days).pipe(catchError(() => of([] as Candle[]))).subscribe((candles) => {
      if (this.selected()?.symbol !== symbol) return;
      this.candles.set(candles);
      this.historyLoading.set(false);
    });
  }

  private extreme(pick: (c: Candle) => number, fn: (...v: number[]) => number): number | null {
    const values = this.candles().map((c) => Number(pick(c))).filter((v) => Number.isFinite(v));
    return values.length ? fn(...values) : null;
  }
}
