import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { forkJoin } from 'rxjs';
import {
  ClientSegmentActivity, InstrumentActivity, MarketActivity, ReportingApi, ReportingOverview, VolumeBucket, errorMessage,
} from './api.service';
import { NavHeaderComponent } from './nav-header.component';
import { displaySymbol, money, segmentLabel } from './format';

type Period = 'month' | 'quarter' | 'ytd';

interface Kpi {
  label: string;
  value: string;
  delta: string;
  trend: 'up' | 'down' | 'muted';
}

@Component({
  selector: 'app-insights',
  imports: [DatePipe, NavHeaderComponent],
  template: `
    <app-nav-header [internal]="true" />
    <main class="page">
      <div class="head-row">
        <div>
          <h1 class="page-title">Trading activity</h1>
          <p class="page-subtitle flush">Volumes, instruments and client behaviour across the platform.</p>
        </div>
        <div class="pill-row" role="tablist" aria-label="Reporting period">
          @for (p of periods; track p.key) {
            <button type="button" role="tab" class="pill" [attr.aria-selected]="period() === p.key" [class.active]="period() === p.key" (click)="select(p.key)">{{ p.label }}</button>
          }
        </div>
      </div>

      @if (error()) { <p class="alert error" role="alert">{{ error() }}</p> }

      <div class="kpis">
        @for (k of kpis(); track k.label) {
          <div class="card kpi">
            <p class="label">{{ k.label }}</p>
            <p class="num stat-value">{{ k.value }}</p>
            <p class="delta {{ k.trend }}">{{ k.delta }}</p>
          </div>
        }
      </div>

      <div class="grid">
        <section class="card">
          <h2>Traded value by week</h2>
          @if (volume().length === 0) {
            <p class="empty-chart">No trades in this period.</p>
          } @else {
            <div class="bars" role="img" [attr.aria-label]="volumeLabel()">
              @for (b of bars(); track b.key) {
                <div class="bar-col" [title]="b.title">
                  <span class="bar-value num">{{ b.short }}</span>
                  <div class="bar" [style.height.%]="b.height"></div>
                  <span class="bar-label">{{ b.label }}</span>
                </div>
              }
            </div>
          }
        </section>

        <section class="card">
          <h2>Order outcomes</h2>
          @if (overview(); as o) {
            <div class="stack" role="img" [attr.aria-label]="outcomeLabel()">
              @for (s of outcomes(); track s.label) {
                @if (s.pct > 0) { <div [style.width.%]="s.pct" [style.background]="s.color"></div> }
              }
            </div>
            <ul class="legend">
              @for (s of outcomes(); track s.label) {
                <li><span class="dot" [style.background]="s.color"></span>{{ s.label }}<span class="num bold">{{ s.count }} · {{ s.pct.toFixed(0) }}%</span></li>
              }
            </ul>
          }
        </section>
      </div>

      <div class="grid">
        <section class="card flush">
          <div class="card-head"><h2>Most active instruments</h2></div>
          @if (instruments().length === 0) { <p class="empty">No activity in this period.</p> } @else {
            <div class="table-wrap">
              <table class="data">
                <thead><tr><th>#</th><th>Instrument</th><th>Market</th><th class="right">Orders</th><th class="right">Fills</th><th class="right">Traded value</th></tr></thead>
                <tbody>
                  @for (i of instruments().slice(0, 10); track i.symbol; let n = $index) {
                    <tr>
                      <td class="muted num">{{ n + 1 }}</td>
                      <td class="bold">{{ fmt.displaySymbol(i.symbol) }}</td>
                      <td class="muted">{{ fmt.segmentLabel(i.segment) }}</td>
                      <td class="num right">{{ i.orderCount }}</td>
                      <td class="num right">{{ i.fillCount }}</td>
                      <td class="num right bold">{{ fmt.money(i.totalNotional, 'USD') }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
        </section>

        <div>
          <section class="card flush">
            <div class="card-head"><h2>By market</h2></div>
            @if (markets().length === 0) { <p class="empty">No activity in this period.</p> } @else {
              <div class="table-wrap">
                <table class="data">
                  <thead><tr><th>Market</th><th class="right">Orders</th><th class="right">Traded value</th></tr></thead>
                  <tbody>
                    @for (m of markets(); track m.segment) {
                      <tr><td>{{ fmt.segmentLabel(m.segment) }}</td><td class="num right">{{ m.orderCount }}</td><td class="num right bold">{{ fmt.money(m.totalNotional, 'USD') }}</td></tr>
                    }
                  </tbody>
                </table>
              </div>
            }
          </section>

          <section class="card flush">
            <div class="card-head"><h2>Clients by risk profile</h2></div>
            @if (segments().length === 0) { <p class="empty">No activity in this period.</p> } @else {
              <div class="table-wrap">
                <table class="data">
                  <thead><tr><th>Risk profile</th><th class="right">Clients</th><th class="right">Orders</th><th class="right">Traded value</th></tr></thead>
                  <tbody>
                    @for (s of segments(); track s.riskProfile) {
                      <tr><td>{{ s.riskProfile }}</td><td class="num right">{{ s.clientCount }}</td><td class="num right">{{ s.orderCount }}</td><td class="num right bold">{{ fmt.money(s.totalNotional, 'USD') }}</td></tr>
                    }
                  </tbody>
                </table>
              </div>
            }
          </section>
        </div>
      </div>

      <p class="footnote">
        Figures come from a reporting snapshot that refreshes on its own schedule, so these queries never compete with client order flow.
        @if (overview()?.dataAsOf) { Snapshot taken {{ overview()!.dataAsOf | date: 'd MMM y, HH:mm:ss' }}. }
        Values are in US dollars, the account currency.
      </p>
    </main>
  `,
  styles: [`
    .head-row { display: flex; justify-content: space-between; align-items: flex-end; flex-wrap: wrap; gap: 16px; margin-bottom: 24px; }
    .page-subtitle.flush { margin: 0; }
    .kpis { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(220px, 100%), 1fr)); gap: 20px; margin-bottom: 24px; }
    .kpi { margin: 0; }
    .delta { font-size: 12px; font-weight: 600; margin: 8px 0 0; }
    .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(440px, 100%), 1fr)); gap: 24px; align-items: start; }
    h2 { margin-bottom: 18px; }
    .card-head h2 { margin: 0; }
    .bars { display: flex; align-items: flex-end; gap: 12px; height: 190px; }
    .bar-col { flex: 1; height: 100%; display: flex; flex-direction: column; align-items: center; justify-content: flex-end; gap: 6px; min-width: 0; }
    .bar { width: 100%; max-width: 40px; background: var(--brand); border-radius: 6px 6px 2px 2px; min-height: 2px; }
    .bar-value { font-size: 11px; color: var(--text-muted); white-space: nowrap; }
    .bar-label { font-size: 11px; color: var(--text-muted); white-space: nowrap; }
    .empty-chart { color: var(--text-muted); font-size: 14px; height: 120px; display: flex; align-items: center; margin: 0; }
    .stack { display: flex; height: 16px; border-radius: 999px; overflow: hidden; margin-bottom: 18px; background: var(--surface-alt); }
    .legend { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 10px; font-size: 13px; }
    .legend li { display: flex; align-items: center; gap: 8px; }
    .legend .num { margin-left: auto; }
    .dot { width: 10px; height: 10px; border-radius: 999px; display: inline-block; }
  `],
})
export class InsightsComponent implements OnInit {
  private readonly reporting = inject(ReportingApi);

  protected readonly fmt = { money, segmentLabel, displaySymbol };
  protected readonly periods: { key: Period; label: string }[] = [
    { key: 'month', label: 'Last 30 days' },
    { key: 'quarter', label: 'Last 90 days' },
    { key: 'ytd', label: 'Year to date' },
  ];

  protected readonly period = signal<Period>('month');
  protected readonly overview = signal<ReportingOverview | null>(null);
  protected readonly prior = signal<ReportingOverview | null>(null);
  protected readonly volume = signal<VolumeBucket[]>([]);
  protected readonly instruments = signal<InstrumentActivity[]>([]);
  protected readonly markets = signal<MarketActivity[]>([]);
  protected readonly segments = signal<ClientSegmentActivity[]>([]);
  protected readonly error = signal('');

  protected readonly kpis = computed<Kpi[]>(() => {
    const now = this.overview();
    const before = this.prior();
    const rate = (o: ReportingOverview | null) => (o && o.totalOrders ? (o.filledOrders / o.totalOrders) * 100 : 0);
    const change = (a: number, b: number | undefined): Pick<Kpi, 'delta' | 'trend'> => {
      if (!before || !b) return { delta: 'No prior-period activity', trend: 'muted' };
      const pct = ((a - b) / b) * 100;
      return { delta: `${pct >= 0 ? '+' : '−'}${Math.abs(pct).toFixed(1)}% vs prior period`, trend: pct >= 0 ? 'up' : 'down' };
    };
    const pointChange = rate(now) - rate(before);
    return [
      { label: 'Traded value', value: money(now?.totalNotional ?? 0, 'USD'), ...change(Number(now?.totalNotional ?? 0), Number(before?.totalNotional)) },
      { label: 'Active clients', value: String(now?.activeClients ?? 0), ...change(now?.activeClients ?? 0, before?.activeClients) },
      { label: 'Orders placed', value: String(now?.totalOrders ?? 0), ...change(now?.totalOrders ?? 0, before?.totalOrders) },
      {
        label: 'Fill rate', value: `${rate(now).toFixed(1)}%`,
        delta: before?.totalOrders ? `${pointChange >= 0 ? '+' : '−'}${Math.abs(pointChange).toFixed(1)} pts vs prior period` : 'No prior-period activity',
        trend: !before?.totalOrders ? 'muted' : pointChange >= 0 ? 'up' : 'down',
      },
    ];
  });

  protected readonly bars = computed(() => {
    const buckets = this.volume();
    const max = Math.max(...buckets.map((b) => Number(b.totalNotional)), 1);
    return buckets.map((b) => {
      const start = new Date(b.periodStart);
      const value = Number(b.totalNotional);
      return {
        key: b.periodStart,
        label: start.toLocaleDateString(undefined, { day: 'numeric', month: 'short' }),
        short: compact(value),
        title: `Week of ${start.toLocaleDateString()}: ${money(value, 'USD')} across ${b.orderCount} orders`,
        height: Math.max(2, (value / max) * 100),
      };
    });
  });

  protected readonly volumeLabel = computed(() => this.bars().map((b) => b.title).join('; '));

  protected readonly outcomes = computed(() => {
    const o = this.overview();
    const total = o?.totalOrders || 0;
    const open = o ? o.totalOrders - o.filledOrders - o.rejectedOrders : 0;
    const pct = (n: number) => (total ? (n / total) * 100 : 0);
    return [
      { label: 'Filled', count: o?.filledOrders ?? 0, pct: pct(o?.filledOrders ?? 0), color: 'var(--green)' },
      { label: 'Open / in flight', count: open, pct: pct(open), color: 'var(--brand)' },
      { label: 'Rejected', count: o?.rejectedOrders ?? 0, pct: pct(o?.rejectedOrders ?? 0), color: 'var(--red)' },
    ];
  });

  protected readonly outcomeLabel = computed(() => this.outcomes().map((s) => `${s.label} ${s.count}`).join(', '));

  ngOnInit(): void {
    this.load();
  }

  select(period: Period): void {
    this.period.set(period);
    this.load();
  }

  private load(): void {
    const { from, to, priorFrom } = periodWindow(this.period());
    forkJoin({
      overview: this.reporting.overview(from, to),
      prior: this.reporting.overview(priorFrom, from),
      volume: this.reporting.volume(from, to, 'week'),
      instruments: this.reporting.instruments(from, to),
      markets: this.reporting.markets(from, to),
      segments: this.reporting.clientSegments(from, to),
    }).subscribe({
      next: (r) => {
        this.overview.set(r.overview);
        this.prior.set(r.prior);
        this.volume.set(r.volume);
        this.instruments.set(r.instruments);
        this.markets.set(r.markets);
        this.segments.set(r.segments);
        this.error.set('');
      },
      error: (e) => this.error.set(errorMessage(e, 'Reporting data could not be loaded.')),
    });
  }
}

/** The chosen period and the equally long period just before it, for comparison. */
function periodWindow(period: Period): { from: Date; to: Date; priorFrom: Date } {
  const to = new Date();
  const from = new Date(to);
  if (period === 'month') {
    from.setDate(from.getDate() - 30);
  } else if (period === 'quarter') {
    from.setDate(from.getDate() - 90);
  } else {
    from.setMonth(0, 1);
    from.setHours(0, 0, 0, 0);
  }
  const priorFrom = new Date(from.getTime() - (to.getTime() - from.getTime()));
  return { from, to, priorFrom };
}

function compact(value: number): string {
  return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', notation: 'compact', maximumFractionDigits: 1 }).format(value);
}
