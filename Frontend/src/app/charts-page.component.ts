import { Component } from '@angular/core';
import { TradingviewChartComponent } from './tradingview-chart.component';

@Component({
  selector: 'app-charts-page',
  standalone: true,
  imports: [TradingviewChartComponent],
  template: `
    <main class="page subpage charts-page">
      <p class="section-label">Visual market analysis</p>
      <h1>Charts</h1>
      <p class="lead">
        Use the TradingView search built into the chart toolbar to switch between stocks.
        The page now focuses on a single large chart at a time.
      </p>

      <section class="charts-workspace">
        <div class="chart-panel">
          <app-tradingview-chart></app-tradingview-chart>
        </div>
      </section>
    </main>
  `,
  styles: [`
    .charts-page {
      width: min(1480px, 100%);
      text-align: center;
    }

    .section-label {
      text-align: center;
    }

    .charts-page h1 {
      text-align: center;
      margin-bottom: 0.5rem;
    }

    .charts-page .lead {
      max-width: 600px;
      margin-left: auto;
      margin-right: auto;
      margin-bottom: 2rem;
    }

    .charts-workspace {
      margin-top: 2rem;
      display: flex;
      justify-content: center;
    }

    .chart-panel {
      width: 100%;
      padding: 1.5rem;
      background: linear-gradient(135deg, rgba(25, 30, 45, 0.8), rgba(20, 25, 40, 0.8));
      border: 1px solid rgba(59, 130, 246, 0.2);
      border-radius: 1rem;
      box-shadow: 0 16px 32px rgba(0, 0, 0, 0.6);
    }
  `],
})
export class ChartsPageComponent {}
