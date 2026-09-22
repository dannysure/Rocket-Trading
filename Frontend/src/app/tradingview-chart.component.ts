import { DOCUMENT, isPlatformBrowser } from '@angular/common';
import {
  AfterViewInit,
  Component,
  ElementRef,
  Inject,
  Input,
  OnChanges,
  OnDestroy,
  PLATFORM_ID,
  SimpleChanges,
  ViewChild,
} from '@angular/core';

@Component({
  selector: 'app-tradingview-chart',
  standalone: true,
  template: `
    <div #container class="tradingview-widget-container">
      <div #widgetHost class="tradingview-widget-container__widget"></div>
      <div class="tradingview-widget-copyright">
        <a [href]="copyrightUrl" rel="noopener nofollow" target="_blank">
          <span class="blue-text">{{ symbolLabel }} stock chart</span>
        </a>
        <span class="trademark"> by TradingView</span>
      </div>
    </div>
  `,
  styles: [`
    :host {
      display: block;
      min-height: 100%;
    }

    .tradingview-widget-container {
      height: 100%;
      width: 100%;
    }

    .tradingview-widget-container__widget {
      height: clamp(60rem, 95vh, 90rem);
      width: 100%;
    }

    .tradingview-widget-copyright {
      margin-top: 1rem;
      font-size: 0.875rem;
      color: var(--text-secondary);
      text-align: center;
    }

    .tradingview-widget-copyright a {
      color: #60a5fa;
      text-decoration: none;
      transition: color 0.2s ease;
    }

    .tradingview-widget-copyright a:hover {
      color: #93c5fd;
    }
  `],
})
export class TradingviewChartComponent implements AfterViewInit, OnChanges, OnDestroy {
  @Input() symbol = 'NASDAQ:AAPL';

  @ViewChild('container', { static: true })
  private readonly containerRef!: ElementRef<HTMLDivElement>;

  @ViewChild('widgetHost', { static: true })
  private readonly widgetHostRef!: ElementRef<HTMLDivElement>;

  private readonly isBrowser: boolean;
  private isViewReady = false;

  constructor(
    @Inject(DOCUMENT) private readonly document: Document,
    @Inject(PLATFORM_ID) private readonly platformId: object,
  ) {
    this.isBrowser = isPlatformBrowser(this.platformId);
  }

  get symbolLabel(): string {
    return this.symbol.split(':').at(-1) ?? this.symbol;
  }

  get copyrightUrl(): string {
    return `https://www.tradingview.com/symbols/${this.symbol.replace(':', '-')}/`;
  }

  ngAfterViewInit(): void {
    this.isViewReady = true;
    this.renderWidget();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['symbol'] && this.isViewReady) {
      this.renderWidget();
    }
  }

  ngOnDestroy(): void {
    if (this.isBrowser && this.isViewReady) {
      this.clearWidget();
    }
  }

  private renderWidget(): void {
    if (!this.isBrowser) {
      return;
    }

    const container = this.containerRef.nativeElement;
    this.clearWidget();

    const script = this.document.createElement('script');
    script.src = 'https://s3.tradingview.com/external-embedding/embed-widget-advanced-chart.js';
    script.type = 'text/javascript';
    script.async = true;
    script.dataset['tradingviewWidget'] = 'advanced-chart';
    script.text = JSON.stringify({
      allow_symbol_change: true,
      autosize: true,
      backgroundColor: '#18181b',
      calendar: false,
      compareSymbols: [],
      details: false,
      gridColor: 'rgba(255, 255, 255, 0.1)',
      hide_legend: false,
      hide_side_toolbar: false,
      hide_top_toolbar: false,
      hide_volume: false,
      hotlist: false,
      interval: 'D',
      locale: 'en',
      save_image: true,
      style: '1',
      studies: [],
      support_host: 'https://www.tradingview.com',
      symbol: this.symbol,
      theme: 'dark',
      timezone: 'Etc/UTC',
      watchlist: [],
      withdateranges: true,
    });

    container.appendChild(script);
  }

  private clearWidget(): void {
    this.widgetHostRef.nativeElement.replaceChildren();
    this.containerRef.nativeElement
      .querySelectorAll('script[data-tradingview-widget="advanced-chart"]')
      .forEach((scriptElement) => scriptElement.remove());
  }
}
