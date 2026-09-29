import { CommonModule, CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { catchError, forkJoin, map, of } from 'rxjs';
import { MarketDataService, MarketQuote } from './market-data.service';
import { TradingviewChartComponent } from './tradingview-chart.component';

interface TickerSpec {
  symbol: string;
  name: string;
  accent: string;
  note: string;
}

interface TickerCardVm {
  spec: TickerSpec;
  quote: MarketQuote | null;
}

interface HomeMarketVm {
  cards: TickerCardVm[];
  updatedAt: string;
  disclaimer: string;
}

const MARKET_TICKERS: TickerSpec[] = [
  {
    symbol: 'AAPL',
    name: 'Apple',
    accent: 'tech',
    note: 'Large-cap tech momentum',
  },
  {
    symbol: 'BTC-USD',
    name: 'Bitcoin',
    accent: 'crypto',
    note: 'Digital asset volatility',
  },
];

@Component({
  selector: 'app-home-page',
  standalone: true,
  imports: [CommonModule, RouterLink, TradingviewChartComponent, CurrencyPipe, DatePipe, DecimalPipe],
  template: `
    <main class="page home-page">
      <section class="hero">
        <div class="hero-copy">
          <p class="section-label">Live market intelligence</p>
          <h1>Prices, context, and a compact SPY chart on one fast homepage.</h1>
          <p class="lead">
            Rocket Trading now pulls live quotes for AAPL and BTC-USD, then pairs them with a
            smaller SPY chart that stays readable on any screen size.
          </p>

          <div class="hero-actions">
            <a class="button button-primary" href="#live-market">View live market</a>
            <a class="button button-secondary" routerLink="/charts">Open full charts</a>
          </div>

          <div class="hero-badges">
            <span>Responsive layout</span>
            <span>Realtime quotes</span>
            <span>TradingView chart</span>
          </div>
        </div>

        <aside class="market-panel" id="live-market" aria-label="Live market dashboard">
          <div class="panel-header">
            <div>
              <p class="panel-kicker">Live ticker feed</p>
              <h2>Launch Pad</h2>
            </div>
            <span class="live-pill">Live</span>
          </div>

          <ng-container *ngIf="market$ | async as market; else loadingState">
            <div class="ticker-grid">
              <article
                *ngFor="let card of market.cards"
                class="ticker-card"
                [class.is-positive]="(card.quote?.change ?? 0) >= 0"
              >
                <ng-container *ngIf="card.quote as quote; else unavailableCard">
                  <div class="ticker-top">
                    <div>
                      <p class="ticker-symbol">{{ card.spec.symbol }}</p>
                      <h3>{{ card.spec.name }}</h3>
                    </div>
                    <span class="trend-badge">
                      {{ quote.change >= 0 ? '+' : '' }}{{ quote.changePercent | number : '1.2-2' }}%
                    </span>
                  </div>

                  <div class="ticker-price">
                    {{ quote.price | currency : quote.currency : 'symbol' : '1.2-2' }}
                  </div>

                  <div class="ticker-meta">
                    <span>{{ card.spec.note }}</span>
                    <span>
                      {{ quote.change >= 0 ? '▲' : '▼' }}
                      {{ quote.change | currency : quote.currency : 'symbol' : '1.2-2' }}
                    </span>
                    <span>Bid {{ quote.bid | currency : quote.currency : 'symbol' : '1.2-2' }}</span>
                    <span>Ask {{ quote.ask | currency : quote.currency : 'symbol' : '1.2-2' }}</span>
                  </div>
                </ng-container>

                <ng-template #unavailableCard>
                  <div class="ticker-top">
                    <div>
                      <p class="ticker-symbol">{{ card.spec.symbol }}</p>
                      <h3>{{ card.spec.name }}</h3>
                    </div>
                    <span class="trend-badge muted">Unavailable</span>
                  </div>
                  <div class="ticker-price muted">No quote available</div>
                </ng-template>
              </article>
            </div>

            <section class="chart-card" aria-label="Compact SPY chart">
              <div class="chart-card-header">
                <div>
                  <p class="panel-kicker">Compact chart</p>
                  <h3>SPY</h3>
                </div>
                <span>Scaled to the screen</span>
              </div>

              <div class="chart-shell">
                <app-tradingview-chart symbol="AMEX:SPY"></app-tradingview-chart>
              </div>
            </section>

            <p class="market-footnote">
              Updated {{ market.updatedAt | date : 'short' }} · {{ market.disclaimer }}
            </p>
          </ng-container>

          <ng-template #loadingState>
            <div class="loading-state">Loading live market data...</div>
          </ng-template>
        </aside>
      </section>

      <section class="feature-grid" aria-label="Platform highlights">
        <article class="feature-card">
          <h2>Fast quote view</h2>
          <p>See price, spread, and change at a glance for the instruments you care about most.</p>
        </article>
        <article class="feature-card">
          <h2>Compact charting</h2>
          <p>Review SPY in a small, responsive chart panel that adapts to the screen size.</p>
        </article>
        <article class="feature-card">
          <h2>Clean hierarchy</h2>
          <p>Keep the homepage readable while still surfacing live market detail and context.</p>
        </article>
      </section>
    </main>
  `,
  styles: [
    `
      .home-page {
        width: min(1480px, 100%);
      }

      .hero {
        display: grid;
        grid-template-columns: minmax(0, 1.05fr) minmax(0, 0.95fr);
        gap: 2rem;
        align-items: stretch;
      }

      .hero-copy {
        padding: 3rem;
        border: 1px solid rgba(59, 130, 246, 0.2);
        border-radius: 1.2rem;
        background: linear-gradient(135deg, rgba(59, 130, 246, 0.12), rgba(147, 51, 234, 0.05));
        box-shadow: 0 14px 30px rgba(0, 0, 0, 0.35);
        text-align: left;
      }

      .hero-copy h1,
      .panel-header h2,
      .feature-card h2,
      .chart-card h3 {
        margin: 0;
        color: var(--text-primary);
        line-height: 1.15;
        font-weight: 800;
        letter-spacing: -0.02em;
      }

      .hero-copy h1 {
        max-width: 14ch;
        font-size: clamp(2.4rem, 4vw, 4.6rem);
      }

      .section-label,
      .panel-kicker {
        margin: 0 0 1rem;
        color: #60a5fa;
        text-transform: uppercase;
        letter-spacing: 0.16rem;
        font-size: 0.75rem;
        font-weight: 800;
      }

      .lead {
        margin: 1.4rem 0 0;
        max-width: 56ch;
        font-size: 1.08rem;
        line-height: 1.8;
        color: var(--text-secondary);
      }

      .hero-actions {
        display: flex;
        flex-wrap: wrap;
        gap: 1rem;
        margin-top: 2rem;
      }

      .button {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        min-width: 11rem;
        padding: 0.95rem 1.4rem;
        border-radius: 0.65rem;
        text-decoration: none;
        font-weight: 700;
        letter-spacing: 0.01rem;
        transition: transform 0.2s ease, box-shadow 0.2s ease, background 0.2s ease;
      }

      .button:hover {
        transform: translateY(-1px);
      }

      .button-primary {
        background: linear-gradient(135deg, #3b82f6, #2563eb);
        color: var(--text-primary);
        box-shadow: 0 12px 24px rgba(59, 130, 246, 0.28);
      }

      .button-secondary {
        border: 1px solid rgba(96, 165, 250, 0.55);
        color: var(--text-primary);
        background: rgba(15, 23, 42, 0.15);
      }

      .hero-badges {
        display: flex;
        flex-wrap: wrap;
        gap: 0.75rem;
        margin-top: 2rem;
      }

      .hero-badges span,
      .live-pill,
      .trend-badge {
        display: inline-flex;
        align-items: center;
        border-radius: 999px;
        font-size: 0.8rem;
        font-weight: 700;
      }

      .hero-badges span {
        padding: 0.45rem 0.8rem;
        border: 1px solid rgba(255, 255, 255, 0.08);
        background: rgba(15, 23, 42, 0.2);
        color: var(--text-secondary);
      }

      .market-panel {
        display: flex;
        flex-direction: column;
        gap: 1rem;
        padding: 1.5rem;
        border-radius: 1.2rem;
        border: 1px solid rgba(59, 130, 246, 0.2);
        background: linear-gradient(135deg, rgba(25, 30, 45, 0.92), rgba(20, 25, 40, 0.92));
        box-shadow: 0 16px 32px rgba(0, 0, 0, 0.45);
      }

      .panel-header,
      .chart-card-header,
      .ticker-top,
      .ticker-meta {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 1rem;
      }

      .panel-header h2 {
        font-size: 1.6rem;
      }

      .live-pill {
        padding: 0.45rem 0.75rem;
        background: rgba(34, 197, 94, 0.12);
        color: #86efac;
        border: 1px solid rgba(34, 197, 94, 0.3);
      }

      .ticker-grid {
        display: grid;
        grid-template-columns: repeat(2, minmax(0, 1fr));
        gap: 1rem;
      }

      .ticker-card,
      .chart-card {
        border-radius: 1rem;
        border: 1px solid rgba(255, 255, 255, 0.06);
        background: rgba(12, 16, 28, 0.7);
      }

      .ticker-card {
        padding: 1rem;
      }

      .ticker-card.is-positive {
        box-shadow: inset 0 0 0 1px rgba(34, 197, 94, 0.12);
      }

      .ticker-symbol,
      .market-footnote,
      .ticker-meta,
      .chart-card-header span,
      .loading-state {
        color: var(--text-secondary);
      }

      .ticker-symbol {
        margin: 0 0 0.2rem;
        font-size: 0.75rem;
        letter-spacing: 0.12rem;
        text-transform: uppercase;
      }

      .ticker-card h3 {
        margin: 0;
        font-size: 1rem;
      }

      .trend-badge {
        padding: 0.35rem 0.7rem;
        background: rgba(59, 130, 246, 0.12);
        color: #bfdbfe;
      }

      .trend-badge.muted,
      .ticker-price.muted {
        color: var(--text-secondary);
        background: rgba(255, 255, 255, 0.04);
      }

      .ticker-price {
        margin: 0.8rem 0 0.6rem;
        font-size: clamp(1.4rem, 2vw, 2rem);
        font-weight: 800;
        letter-spacing: -0.03em;
        color: var(--text-primary);
      }

      .ticker-meta {
        flex-wrap: wrap;
        font-size: 0.8rem;
        gap: 0.5rem 0.75rem;
      }

      .chart-card {
        padding: 1rem;
      }

      .chart-card-header {
        margin-bottom: 0.75rem;
      }

      .chart-card h3 {
        font-size: 1.2rem;
      }

      .chart-shell {
        height: clamp(18rem, 34vh, 24rem);
      }

      .chart-shell app-tradingview-chart {
        display: block;
        height: 100%;
      }

      .market-footnote,
      .loading-state {
        margin: 0;
        font-size: 0.82rem;
        line-height: 1.6;
      }

      .loading-state {
        padding: 2rem 0.25rem;
      }

      .feature-grid {
        display: grid;
        grid-template-columns: repeat(3, minmax(0, 1fr));
        gap: 1rem;
        margin-top: 2rem;
      }

      .feature-card {
        padding: 1.5rem;
        border-radius: 1rem;
        border: 1px solid rgba(59, 130, 246, 0.16);
        background: linear-gradient(135deg, rgba(25, 30, 45, 0.8), rgba(20, 25, 40, 0.8));
        box-shadow: 0 12px 24px rgba(0, 0, 0, 0.32);
      }

      .feature-card h2 {
        font-size: 1.15rem;
        margin-bottom: 0.65rem;
      }

      .feature-card p {
        margin: 0;
        line-height: 1.7;
        color: var(--text-secondary);
      }

      @media (max-width: 980px) {
        .hero {
          grid-template-columns: 1fr;
        }

        .ticker-grid,
        .feature-grid {
          grid-template-columns: 1fr;
        }

        .hero-copy {
          padding: 2rem;
        }
      }
    `,
  ],
})
export class HomePageComponent {
  private readonly marketData = inject(MarketDataService);

  readonly market$ = forkJoin(
    MARKET_TICKERS.map((spec) =>
      this.marketData.getQuote(spec.symbol).pipe(
        catchError(() => of(null)),
      ),
    ),
  ).pipe(
    map((quotes): HomeMarketVm => ({
      cards: MARKET_TICKERS.map((spec, index) => ({
        spec,
        quote: quotes[index],
      })),
      updatedAt:
        quotes.find((quote): quote is MarketQuote => quote !== null)?.asOf ??
        new Date().toISOString(),
      disclaimer:
        quotes.find((quote): quote is MarketQuote => quote !== null)?.disclaimer ??
        'Educational data. Not for investment use.',
    })),
  );
}
