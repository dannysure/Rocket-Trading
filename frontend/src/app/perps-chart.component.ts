import { Component, DestroyRef, ElementRef, InjectionToken, afterNextRender, effect, inject, input, signal, viewChild } from '@angular/core';
import type { IChartApi, IPriceLine, ISeriesApi, Time, UTCTimestamp } from 'lightweight-charts';
import { Candle } from './perps.math';

type ChartLibrary = Pick<typeof import('lightweight-charts'), 'createChart' | 'CandlestickSeries' | 'ColorType' | 'LineStyle' | 'TickMarkType'>;

/** Loads TradingView Lightweight Charts on demand, in the browser only; tests provide a fake. */
export const CHART_LIBRARY = new InjectionToken<() => Promise<ChartLibrary>>('CHART_LIBRARY', {
  factory: () => () => import('lightweight-charts'),
});

export interface ChartLine {
  price: number;
  title: string;
  tone: 'brand' | 'muted' | 'down';
  dashed?: boolean;
}

/** A TradingView candlestick chart: history via candles, the in-progress candle via live, plus labelled price lines. */
@Component({
  selector: 'app-perps-chart',
  template: `
    <div #host class="host" role="img" [attr.aria-label]="label()"></div>
    @if (failed()) { <p class="chart-failed">The chart could not be loaded.</p> }
  `,
  styles: [`
    :host { display: block; position: relative; }
    .host { height: 300px; }
    .chart-failed { position: absolute; inset: 0; margin: 0; display: flex; align-items: center; justify-content: center; color: var(--text-muted); font-size: 14px; }
  `],
})
export class PerpsChartComponent {
  readonly candles = input<Candle[]>([]);
  readonly live = input<Candle | null>(null);
  readonly lines = input<ChartLine[]>([]);
  readonly label = input('');

  protected readonly failed = signal(false);
  private readonly host = viewChild.required<ElementRef<HTMLElement>>('host');
  private readonly ready = signal(false);
  private library: ChartLibrary | null = null;
  private chart: IChartApi | null = null;
  private series: ISeriesApi<'Candlestick'> | null = null;
  private priceLines: IPriceLine[] = [];
  private shownCandles: Candle[] | null = null;
  private lastTime = 0;

  constructor() {
    const load = inject(CHART_LIBRARY);
    let destroyed = false;
    inject(DestroyRef).onDestroy(() => {
      destroyed = true;
      this.chart?.remove();
      this.chart = null;
    });

    afterNextRender(() => {
      load()
        .then((library) => {
          if (destroyed) return;
          this.library = library;
          this.create(library);
          this.ready.set(true);
        })
        .catch(() => this.failed.set(true));
    });

    effect(() => {
      const candles = this.candles();
      const live = this.live();
      if (!this.ready() || !this.series) return;
      if (candles !== this.shownCandles) {
        this.shownCandles = candles;
        this.series.setData(candles.map(toBar));
        this.lastTime = candles.length ? candles[candles.length - 1].time : 0;
        this.chart?.timeScale().scrollToRealTime();
      }
      // The chart only accepts updates to its newest bar or a later one
      if (live && live.time >= this.lastTime) {
        this.series.update(toBar(live));
        this.lastTime = live.time;
      }
    });

    effect(() => {
      const lines = this.lines();
      if (!this.ready() || !this.series || !this.library) return;
      for (const line of this.priceLines) this.series.removePriceLine(line);
      const colors = themeColors();
      this.priceLines = lines.map((line) => this.series!.createPriceLine({
        price: line.price,
        title: line.title,
        color: line.tone === 'brand' ? colors.brand : line.tone === 'down' ? colors.red : colors.muted,
        lineWidth: 1,
        lineStyle: line.dashed ? this.library!.LineStyle.Dashed : this.library!.LineStyle.Solid,
        axisLabelVisible: true,
      }));
    });
  }

  private create(library: ChartLibrary): void {
    const colors = themeColors();
    const { TickMarkType } = library;
    this.chart = library.createChart(this.host().nativeElement, {
      autoSize: true,
      layout: {
        background: { type: library.ColorType.Solid, color: colors.surface },
        textColor: colors.muted,
        fontFamily: colors.font,
        attributionLogo: true,
      },
      grid: { vertLines: { color: colors.grid }, horzLines: { color: colors.grid } },
      rightPriceScale: { borderColor: colors.border },
      timeScale: {
        borderColor: colors.border,
        timeVisible: true,
        secondsVisible: false,
        // Candle times are UTC; label the axis in the viewer's own time zone
        tickMarkFormatter: (time: Time, type: number) => {
          const date = new Date((time as number) * 1000);
          if (type === TickMarkType.Year) return String(date.getFullYear());
          if (type === TickMarkType.Month) return date.toLocaleDateString(undefined, { month: 'short' });
          if (type === TickMarkType.DayOfMonth) return date.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
          return date.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });
        },
      },
      localization: {
        timeFormatter: (time: Time) => new Date((time as number) * 1000)
          .toLocaleString(undefined, { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }),
      },
    });
    this.series = this.chart.addSeries(library.CandlestickSeries, {
      upColor: colors.green,
      downColor: colors.red,
      wickUpColor: colors.green,
      wickDownColor: colors.red,
      borderVisible: false,
      priceLineVisible: false,
    });
  }
}

function toBar(candle: Candle) {
  return { time: candle.time as UTCTimestamp, open: candle.open, high: candle.high, low: candle.low, close: candle.close };
}

// The chart draws on a canvas, so it takes the app's theme as concrete colors
function themeColors() {
  const style = getComputedStyle(document.documentElement);
  const read = (name: string, fallback: string) => style.getPropertyValue(name).trim() || fallback;
  return {
    surface: read('--surface', '#ffffff'),
    border: read('--border', '#dce3f0'),
    grid: read('--surface-alt', '#edf1fa'),
    muted: read('--text-muted', '#5b6b85'),
    brand: read('--brand', '#1f49e6'),
    green: read('--green', '#15803d'),
    red: read('--red', '#b91c1c'),
    font: read('--font-body', 'system-ui, sans-serif'),
  };
}
