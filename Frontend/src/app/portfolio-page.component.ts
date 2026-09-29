import { CommonModule, CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component } from '@angular/core';

interface Holding {
  clientName: string;
  ticker: string;
  quantity: number;
  latestPrice: number;
  marketValue: number;
  pctOfPortfolio: number;
  costBasis: number;
}

interface PortfolioStat {
  label: string;
  value: string;
  detail: string;
}

const HOLDINGS: Holding[] = [
  {
    clientName: 'Alex Morgan',
    ticker: 'AAPL',
    quantity: 145,
    latestPrice: 340.12,
    marketValue: 49317.4,
    pctOfPortfolio: 18.2,
    costBasis: 41850,
  },
  {
    clientName: 'Alex Morgan',
    ticker: 'MSFT',
    quantity: 88,
    latestPrice: 516.78,
    marketValue: 45476.64,
    pctOfPortfolio: 16.8,
    costBasis: 39940,
  },
  {
    clientName: 'Alex Morgan',
    ticker: 'SPY',
    quantity: 72,
    latestPrice: 675.24,
    marketValue: 48617.28,
    pctOfPortfolio: 18.0,
    costBasis: 45120,
  },
  {
    clientName: 'Alex Morgan',
    ticker: 'BTC-USD',
    quantity: 0.82,
    latestPrice: 83780.56,
    marketValue: 68699.06,
    pctOfPortfolio: 25.4,
    costBasis: 58800,
  },
  {
    clientName: 'Alex Morgan',
    ticker: 'GLD',
    quantity: 140,
    latestPrice: 248.31,
    marketValue: 34763.4,
    pctOfPortfolio: 12.9,
    costBasis: 31900,
  },
  {
    clientName: 'Alex Morgan',
    ticker: 'CASH',
    quantity: 1,
    latestPrice: 1,
    marketValue: 15240.88,
    pctOfPortfolio: 5.7,
    costBasis: 15240.88,
  },
];

const PORTFOLIO_STATS: PortfolioStat[] = [
  { label: 'Net liquidation value', value: '$272,114', detail: '+8.4% YTD / +2.1% MTD' },
  { label: 'Unrealized gain', value: '$18,245', detail: 'Average cost basis: $234,510 blended' },
  { label: 'Cash available', value: '$15,241', detail: '5.7% of portfolio' },
  { label: 'Largest position', value: 'BTC-USD', detail: '25.4% allocation' },
];

@Component({
  selector: 'app-portfolio-page',
  standalone: true,
  imports: [CommonModule, CurrencyPipe, DatePipe, DecimalPipe],
  template: `
    <main class="page subpage portfolio-page">
      <section class="page-head">
        <div>
          <p class="section-label">Holdings overview</p>
          <h1>Portfolio</h1>
          <p class="lead">
            A client-friendly snapshot of account value, allocations, concentration risk, and
            current holdings. This view is built to help clients understand where capital is
            deployed at a glance.
          </p>
        </div>

        <aside class="account-summary">
          <p class="summary-kicker">Client</p>
          <h2>Alex Morgan Household</h2>
          <dl>
            <div>
              <dt>Reporting date</dt>
              <dd>{{ reportDate | date : 'mediumDate' }}</dd>
            </div>
            <div>
              <dt>Base currency</dt>
              <dd>USD</dd>
            </div>
            <div>
              <dt>Risk profile</dt>
              <dd>Balanced growth</dd>
            </div>
            <div>
              <dt>Advisor</dt>
              <dd>RTP Wealth Desk</dd>
            </div>
          </dl>
        </aside>
      </section>

      <section class="stats-grid" aria-label="Portfolio metrics">
        <article class="stat-card" *ngFor="let stat of portfolioStats">
          <p>{{ stat.label }}</p>
          <strong>{{ stat.value }}</strong>
          <span>{{ stat.detail }}</span>
        </article>
      </section>

      <section class="dashboard-grid">
        <article class="panel">
          <div class="panel-header">
            <div>
              <p class="panel-kicker">Allocation mix</p>
              <h2>Where the portfolio sits today</h2>
            </div>
            <span>Dummy data</span>
          </div>

          <div class="allocation-list">
            <div class="allocation-row" *ngFor="let holding of holdings">
              <div class="allocation-labels">
                <strong>{{ holding.ticker }}</strong>
                <span>{{ holding.clientName }}</span>
              </div>
              <div class="allocation-bar" [style.--width.%]="holding.pctOfPortfolio">
                <span></span>
              </div>
              <strong class="allocation-value">{{ holding.pctOfPortfolio | number : '1.1-1' }}%</strong>
            </div>
          </div>
        </article>

        <article class="panel performance-panel">
          <div class="panel-header">
            <div>
              <p class="panel-kicker">Portfolio analytics</p>
              <h2>Useful client context</h2>
            </div>
          </div>

          <div class="insight-card">
            <h3>Concentration</h3>
            <p>
              The top three positions represent
              <strong>{{ topThreeAllocation | number : '1.1-1' }}%</strong> of portfolio value.
              Diversification is moderate, with crypto and equity exposure balanced by cash and gold.
            </p>
          </div>

          <div class="insight-card">
            <h3>Risk check</h3>
            <p>
              BTC-USD is the largest position and the main source of volatility. The cash sleeve
              provides flexibility for rebalancing and opportunity deployment.
            </p>
          </div>

          <div class="insight-card">
            <h3>Rebalancing note</h3>
            <p>
              Consider trimming gains from concentrated winners and re-adding to underweight
              defensive assets if the portfolio drifts beyond target allocation bands.
            </p>
          </div>
        </article>
      </section>

      <section class="panel table-panel">
        <div class="panel-header">
          <div>
            <p class="panel-kicker">Current holdings</p>
            <h2>Position detail</h2>
          </div>
          <span>{{ holdings.length }} holdings</span>
        </div>

        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Client full name</th>
                <th>Ticker symbol</th>
                <th>Quantity</th>
                <th>Latest price</th>
                <th>Market value</th>
                <th>% of portfolio</th>
                <th>Gain / loss</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let holding of holdings">
                <td>{{ holding.clientName }}</td>
                <td>
                  <span class="ticker-pill">{{ holding.ticker }}</span>
                </td>
                <td>{{ holding.quantity | number : '1.2-2' }}</td>
                <td>{{ holding.latestPrice | currency : 'USD' : 'symbol' : '1.2-2' }}</td>
                <td>{{ holding.marketValue | currency : 'USD' : 'symbol' : '1.2-2' }}</td>
                <td>{{ holding.pctOfPortfolio | number : '1.1-1' }}%</td>
                <td [class.is-positive]="positionGain(holding) >= 0" [class.is-negative]="positionGain(holding) < 0">
                  {{ positionGain(holding) | currency : 'USD' : 'symbol' : '1.2-2' }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </main>
  `,
  styles: [
    `
      .portfolio-page {
        width: min(1480px, 100%);
      }

      .page-head {
        display: grid;
        grid-template-columns: minmax(0, 1.3fr) minmax(320px, 0.7fr);
        gap: 1.5rem;
        align-items: start;
      }

      .account-summary,
      .panel,
      .stat-card {
        border: 1px solid rgba(59, 130, 246, 0.16);
        border-radius: 1rem;
        background: linear-gradient(135deg, rgba(25, 30, 45, 0.85), rgba(20, 25, 40, 0.85));
        box-shadow: 0 12px 28px rgba(0, 0, 0, 0.35);
      }

      .account-summary {
        padding: 1.5rem;
      }

      .summary-kicker,
      .panel-kicker,
      .section-label {
        margin: 0 0 0.85rem;
        color: #60a5fa;
        text-transform: uppercase;
        letter-spacing: 0.15rem;
        font-size: 0.75rem;
        font-weight: 800;
      }

      .page-head h1,
      .panel h2,
      .insight-card h3 {
        margin: 0;
        color: var(--text-primary);
        line-height: 1.15;
        font-weight: 800;
        letter-spacing: -0.02em;
      }

      .page-head h1 {
        font-size: clamp(2.2rem, 4vw, 3.8rem);
      }

      .lead {
        max-width: 68ch;
        margin: 1rem 0 0;
        color: var(--text-secondary);
        line-height: 1.8;
        font-size: 1.05rem;
      }

      .account-summary dl {
        display: grid;
        grid-template-columns: repeat(2, minmax(0, 1fr));
        gap: 1rem;
        margin: 1.25rem 0 0;
      }

      .account-summary dt {
        font-size: 0.78rem;
        text-transform: uppercase;
        letter-spacing: 0.08rem;
        color: var(--text-secondary);
      }

      .account-summary dd {
        margin: 0.3rem 0 0;
        color: var(--text-primary);
        font-weight: 700;
      }

      .stats-grid {
        display: grid;
        grid-template-columns: repeat(4, minmax(0, 1fr));
        gap: 1rem;
        margin-top: 1.5rem;
      }

      .stat-card {
        padding: 1.25rem;
      }

      .stat-card p,
      .stat-card span,
      .allocation-labels span,
      .table-panel th {
        color: var(--text-secondary);
      }

      .stat-card strong {
        display: block;
        margin: 0.55rem 0 0.35rem;
        color: var(--text-primary);
        font-size: 1.5rem;
      }

      .dashboard-grid {
        display: grid;
        grid-template-columns: minmax(0, 1.1fr) minmax(0, 0.9fr);
        gap: 1rem;
        margin-top: 1.5rem;
      }

      .panel {
        padding: 1.25rem;
      }

      .panel-header {
        display: flex;
        justify-content: space-between;
        align-items: flex-start;
        gap: 1rem;
        margin-bottom: 1rem;
      }

      .panel-header span {
        color: var(--text-secondary);
        font-size: 0.85rem;
      }

      .allocation-list,
      .insight-card {
        display: grid;
        gap: 1rem;
      }

      .allocation-row {
        display: grid;
        grid-template-columns: 140px minmax(0, 1fr) 70px;
        gap: 1rem;
        align-items: center;
      }

      .allocation-labels strong {
        display: block;
        color: var(--text-primary);
      }

      .allocation-bar {
        position: relative;
        height: 0.8rem;
        overflow: hidden;
        border-radius: 999px;
        background: rgba(255, 255, 255, 0.06);
      }

      .allocation-bar span {
        position: absolute;
        inset: 0 auto 0 0;
        width: calc(var(--width) * 1%);
        border-radius: inherit;
        background: linear-gradient(90deg, #3b82f6, #8b5cf6);
      }

      .allocation-value {
        text-align: right;
        color: var(--text-primary);
      }

      .performance-panel {
        display: grid;
        gap: 1rem;
      }

      .insight-card {
        padding: 1rem;
        border-radius: 0.9rem;
        background: rgba(12, 16, 28, 0.7);
        border: 1px solid rgba(255, 255, 255, 0.06);
      }

      .insight-card p {
        margin: 0;
        color: var(--text-secondary);
        line-height: 1.7;
      }

      .table-panel {
        margin-top: 1.5rem;
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
        white-space: nowrap;
      }

      th {
        font-size: 0.78rem;
        text-transform: uppercase;
        letter-spacing: 0.08rem;
        font-weight: 700;
      }

      td {
        color: var(--text-primary);
      }

      .ticker-pill {
        display: inline-flex;
        align-items: center;
        padding: 0.3rem 0.6rem;
        border-radius: 999px;
        background: rgba(59, 130, 246, 0.14);
        color: #bfdbfe;
        font-size: 0.8rem;
        font-weight: 800;
      }

      .is-positive {
        color: #22c55e;
        font-weight: 700;
      }

      .is-negative {
        color: #ef4444;
        font-weight: 700;
      }

      @media (max-width: 1100px) {
        .stats-grid,
        .dashboard-grid,
        .page-head {
          grid-template-columns: 1fr;
        }
      }

      @media (max-width: 760px) {
        .account-summary dl {
          grid-template-columns: 1fr;
        }

        .allocation-row {
          grid-template-columns: 1fr;
        }

        .allocation-value {
          text-align: left;
        }
      }
    `,
  ],
})
export class PortfolioPageComponent {
  readonly holdings = HOLDINGS;
  readonly portfolioStats = PORTFOLIO_STATS;
  readonly reportDate = new Date('2026-09-25T17:43:23Z');

  get topThreeAllocation(): number {
    return this.holdings.slice(0, 3).reduce((sum, holding) => sum + holding.pctOfPortfolio, 0);
  }

  positionGain(holding: Holding): number {
    return holding.marketValue - holding.costBasis;
  }
}
