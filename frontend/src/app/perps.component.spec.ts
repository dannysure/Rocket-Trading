import { TestBed, ComponentFixture } from '@angular/core/testing';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { Observable, Subject, of, throwError } from 'rxjs';
import { PerpsComponent, PERPS_POLL_MS } from './perps.component';
import { CHART_LIBRARY } from './perps-chart.component';
import { PerpAccount, PerpFunding, PerpsApi, PerpsStream, PerpStreamError, PerpTicker } from './perps.service';

const now = Date.now();
// Long 2 / short 1.5 on a 1000 skew scale -> 0.05% premium, already included in the bid and ask
const ticker: PerpTicker = {
  symbol: 'BTC-PERP',
  markPrice: 100.5,
  oraclePrice: 100.4,
  bid: 100,
  ask: 101,
  premium: 0.0005,
  fundingRate: 0.0000125,
  fundingIntervalMinutes: 60,
  nextFundingAt: new Date(now + 12 * 60_000).toISOString(),
  openInterest: 3.5,
  longOpenInterest: 2,
  shortOpenInterest: 1.5,
  maxLeverage: 40,
  takerFeeRate: 0.00045,
  maintenanceMarginRate: 0.0125,
  // Inside the newest history candle's minute
  capturedAt: new Date(1792011610_000).toISOString(),
  delayed: false,
};
const ethTicker: PerpTicker = { ...ticker, symbol: 'ETH-PERP', markPrice: 2442.5, oraclePrice: 2442.4, bid: 2442, ask: 2443, maxLeverage: 25 };

const flatAccount: PerpAccount = {
  accountId: 1,
  currency: 'USD',
  availableCollateral: 1010,
  totalMargin: 0,
  unrealizedPnl: 0,
  accountValue: 1010,
  positions: [],
};

const longAccount: PerpAccount = {
  ...flatAccount,
  totalMargin: 20.2,
  unrealizedPnl: -1,
  accountValue: 1029.2,
  positions: [{
    positionId: 7, symbol: 'BTC-PERP', side: 'LONG', size: 2, entryPrice: 101, markPrice: 100.5,
    positionValue: 201, unrealizedPnl: -1, returnOnEquity: -0.0495, liquidationPrice: 92.1,
    margin: 20.2, leverage: 5, cumulativeFunding: 0, openedAt: new Date().toISOString(),
  }],
};

const history = [
  { time: 1792011540, open: 100, high: 101, low: 99, close: 100.2 },
  { time: 1792011600, open: 100.2, high: 100.6, low: 100.1, close: 100.4 },
];

function createApi(account: PerpAccount = flatAccount, funding: PerpFunding[] = []) {
  return {
    markets: vi.fn(() => of([
      { symbol: 'BTC-PERP', baseAsset: 'BTC', underlyingSymbol: 'X:BTC-USD', quoteCurrency: 'USD', maxLeverage: 40, takerFeeRate: 0.00045, maintenanceMarginRate: 0.0125, skewScale: 1000, maxPremium: 0.01 },
      { symbol: 'ETH-PERP', baseAsset: 'ETH', underlyingSymbol: 'X:ETH-USD', quoteCurrency: 'USD', maxLeverage: 25, takerFeeRate: 0.00045, maintenanceMarginRate: 0.02, skewScale: 30000, maxPremium: 0.01 },
    ])),
    candles: vi.fn((_symbol: string, _interval: number) => of(history)),
    account: vi.fn(() => of<PerpAccount>(account)),
    orders: vi.fn(() => of([])),
    funding: vi.fn(() => of(funding)),
    submit: vi.fn((_request: unknown, _key: string) => of<any>({ orderId: 1, status: 'FILLED', filledSize: 1, fillPrice: 101 })),
  };
}

/** Each (re)subscription opens a new connection, like the real fetch-based stream. */
function createStream() {
  const connections: Subject<PerpTicker[]>[] = [];
  return {
    connections,
    latest: () => connections[connections.length - 1],
    tickers: vi.fn(() => new Observable<PerpTicker[]>((subscriber) => {
      const connection = new Subject<PerpTicker[]>();
      connections.push(connection);
      return connection.subscribe(subscriber);
    })),
  };
}

function createChartLibrary() {
  const series = {
    setData: vi.fn(),
    update: vi.fn(),
    createPriceLine: vi.fn((options: { title: string; price: number }) => options),
    removePriceLine: vi.fn(),
  };
  const chart = {
    addSeries: vi.fn(() => series),
    timeScale: vi.fn(() => ({ scrollToRealTime: vi.fn() })),
    remove: vi.fn(),
  };
  const library = {
    createChart: vi.fn(() => chart),
    CandlestickSeries: {},
    ColorType: { Solid: 'solid' },
    LineStyle: { Solid: 0, Dashed: 2 },
    TickMarkType: { Year: 0, Month: 1, DayOfMonth: 2, Time: 3, TimeWithSeconds: 4 },
  };
  return { library, chart, series };
}

describe('PerpsComponent', () => {
  let fixture: ComponentFixture<PerpsComponent>;
  let api: ReturnType<typeof createApi>;
  let stream: ReturnType<typeof createStream>;
  let charts: ReturnType<typeof createChartLibrary>;

  async function render(account: PerpAccount = flatAccount, startLive = true, pollMs = 0, funding: PerpFunding[] = []) {
    api = createApi(account, funding);
    stream = createStream();
    charts = createChartLibrary();
    await TestBed.configureTestingModule({
      imports: [PerpsComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: PerpsApi, useValue: api },
        { provide: PerpsStream, useValue: stream },
        { provide: PERPS_POLL_MS, useValue: pollMs },
        { provide: CHART_LIBRARY, useValue: () => Promise.resolve(charts.library) },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(PerpsComponent);
    fixture.detectChanges();
    if (startLive) {
      fixture.componentInstance.startLiveUpdates();
      push([ticker, ethTicker]);
    }
    await settle();
  }

  const el = () => fixture.nativeElement as HTMLElement;
  const text = (selector: string) => el().querySelector(selector)?.textContent?.replace(/\s+/g, ' ').trim() ?? '';
  const byTestId = (id: string) => el().querySelector(`[data-testid="${id}"]`) as HTMLElement;
  const push = (tickers: PerpTicker[]) => stream.latest().next(tickers);

  async function settle() {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  async function typeSize(value: string) {
    const input = byTestId('size-input') as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    await settle();
  }

  afterEach(() => {
    fixture?.destroy();
    vi.useRealTimers();
  });

  it('starts dormant: shows the account and history once, but streams and polls nothing until the button is clicked', async () => {
    await render(flatAccount, false);
    expect(text('[data-testid="live-status"]')).toContain('Dormant');
    expect(stream.tickers).not.toHaveBeenCalled();
    expect(api.account).toHaveBeenCalledTimes(1);
    expect(api.orders).toHaveBeenCalledTimes(1);
    expect(api.funding).toHaveBeenCalledTimes(1);
    fixture.componentInstance.refresh();
    fixture.componentInstance.selectMarket('ETH-PERP');
    expect(stream.tickers).not.toHaveBeenCalled();
    expect(api.account).toHaveBeenCalledTimes(1);
    byTestId('live-toggle').click();
    await settle();
    push([ticker, ethTicker]);
    await settle();
    expect(stream.tickers).toHaveBeenCalledTimes(1);
    expect(api.account).toHaveBeenCalledTimes(2);
    expect(text('[data-testid="mark-price"]')).toContain('2,442.50');
    expect(text('[data-testid="live-status"]')).toContain('10:00');
  });

  it('expires after ten minutes, closes the stream, cancels in-flight updates, and can restart', async () => {
    await render(flatAccount, false);
    vi.useFakeTimers();
    fixture.componentInstance.startLiveUpdates();
    const pending = new Subject<PerpAccount>();
    api.account.mockReturnValueOnce(pending);
    fixture.componentInstance.refresh();
    vi.advanceTimersByTime(10 * 60_000);
    expect(fixture.componentInstance.liveActive()).toBe(false);
    expect(stream.latest().observed).toBe(false);
    pending.next(longAccount);
    expect(fixture.componentInstance.account()).toBe(flatAccount);
    fixture.componentInstance.refresh();
    expect(stream.tickers).toHaveBeenCalledTimes(1);
    fixture.componentInstance.startLiveUpdates();
    expect(fixture.componentInstance.liveActive()).toBe(true);
    expect(stream.tickers).toHaveBeenCalledTimes(2);
    fixture.componentInstance.stopLiveUpdates();
    expect(fixture.componentInstance.liveActive()).toBe(false);
    expect(stream.latest().observed).toBe(false);
  });

  it('polls the account only during the session and stops on expiry or navigation', async () => {
    await render(flatAccount, false, 3000);
    vi.useFakeTimers();
    fixture.componentInstance.startLiveUpdates();
    vi.advanceTimersByTime(6000);
    // the one-off load on open, then the session's first refresh and two polls
    expect(api.account).toHaveBeenCalledTimes(4);
    vi.advanceTimersByTime(594000);
    expect(fixture.componentInstance.liveActive()).toBe(false);
    const calls = api.account.mock.calls.length;
    vi.advanceTimersByTime(60000);
    expect(api.account).toHaveBeenCalledTimes(calls);
    fixture.componentInstance.startLiveUpdates();
    fixture.destroy();
    const afterDestroy = api.account.mock.calls.length;
    vi.advanceTimersByTime(6000);
    expect(api.account).toHaveBeenCalledTimes(afterDestroy);
    expect(stream.latest().observed).toBe(false);
  });

  it('reconnects the price stream with backoff after it drops', async () => {
    await render();
    vi.useFakeTimers();
    stream.latest().error(new PerpStreamError(0));
    fixture.detectChanges();
    expect(text('.bar-alert')).toContain('Reconnecting');
    vi.advanceTimersByTime(1000);
    expect(stream.tickers).toHaveBeenCalledTimes(1);
    expect(stream.connections).toHaveLength(2);
    push([{ ...ticker, markPrice: 101.25 }]);
    fixture.detectChanges();
    expect(text('.bar-alert')).toBe('');
    expect(text('[data-testid="mark-price"]')).toContain('101.25');
  });

  it('stops reconnecting when the session is no longer accepted', async () => {
    await render();
    vi.useFakeTimers();
    stream.latest().error(new PerpStreamError(401));
    vi.advanceTimersByTime(60_000);
    fixture.detectChanges();
    expect(stream.connections).toHaveLength(1);
    expect(text('.bar-alert')).toContain('Sign in again');
  });

  it('shows positions and funding history as soon as the page opens', async () => {
    await render(longAccount, false, 0, [{
      fundingId: 1, symbol: 'BTC-PERP', side: 'LONG', size: 2, markPrice: 101.101, fundingRate: 0.0000625, payment: 0.0126,
      fundingTime: new Date().toISOString(),
    }]);
    expect(text('[data-testid="position-row"]')).toContain('BTC-PERP');
    fixture.componentInstance.bottomTab.set('funding');
    await settle();
    expect(el().querySelector('.activity tbody')?.textContent).toContain('0.0063%');
    expect(el().querySelector('.activity tbody')?.textContent).toContain('−$0.01');
  });

  it('uses the shared navigation header', async () => {
    await render();
    expect(el().querySelector('app-nav-header')).toBeTruthy();
  });

  it('shows the market header with mark, oracle, premium, funding and long/short open interest', async () => {
    await render();
    expect(text('[data-testid="market-symbol"]')).toContain('BTC-PERP');
    expect(text('[data-testid="mark-price"]')).toContain('100.5');
    expect(text('[data-testid="oracle-price"]')).toContain('100.4');
    expect(text('[data-testid="premium"]')).toBe('+0.050%');
    expect(text('[data-testid="funding-rate"]')).toContain('0.0013%');
    expect(text('[data-testid="funding-countdown"]')).toMatch(/00:1[12]:\d\d/);
    expect(text('[data-testid="open-interest"]')).toContain('3.5');
    expect(text('[data-testid="open-interest-split"]')).toBe('L 2 · S 1.5');
  });

  it('labels the funding interval from the ticker', async () => {
    await render();
    push([{ ...ticker, fundingIntervalMinutes: 5 }]);
    await settle();
    expect(el().textContent).toContain('Funding (5m)');
  });

  it('switches markets from the market pills using the streamed prices and that market\'s history', async () => {
    await render();
    (el().querySelector('[data-testid="market-ETH-PERP"]') as HTMLElement).click();
    await settle();
    expect(text('[data-testid="mark-price"]')).toContain('2,442.50');
    expect(api.candles).toHaveBeenLastCalledWith('ETH-PERP', 60);
  });

  it('draws TradingView candles from history and extends them with live prices', async () => {
    await render(longAccount);
    expect(api.candles).toHaveBeenCalledWith('BTC-PERP', 60);
    expect(charts.library.createChart).toHaveBeenCalledTimes(1);
    expect(charts.series.setData).toHaveBeenLastCalledWith(history);

    // A tick inside the newest candle's minute extends it
    push([{ ...ticker, oraclePrice: 100.9, capturedAt: new Date(1792011630_000).toISOString() }]);
    await settle();
    expect(charts.series.update).toHaveBeenLastCalledWith({ time: 1792011600, open: 100.2, high: 100.9, low: 100.1, close: 100.9 });
    // The next minute opens a new candle
    push([{ ...ticker, oraclePrice: 100.7, capturedAt: new Date(1792011665_000).toISOString() }]);
    await settle();
    expect(charts.series.update).toHaveBeenLastCalledWith({ time: 1792011660, open: 100.7, high: 100.7, low: 100.7, close: 100.7 });

    const lines = charts.series.createPriceLine.mock.calls.map(([line]) => `${line.title} ${line.price}`);
    expect(lines).toEqual(expect.arrayContaining(['Mark 100.5', 'Entry 101', 'Liq. 92.1']));
  });

  it('reloads history when the candle interval changes', async () => {
    await render();
    byTestId('interval-15m').click();
    await settle();
    expect(api.candles).toHaveBeenLastCalledWith('BTC-PERP', 900);
    expect(byTestId('interval-15m').classList).toContain('active');
  });

  it('labels the order book as simulated depth around the live quote', async () => {
    await render();
    expect(text('[data-testid="order-book"]')).toContain('Simulated depth');
    expect(el().querySelectorAll('[data-testid="ask-level"]').length).toBeGreaterThan(0);
    expect(el().querySelectorAll('[data-testid="bid-level"]').length).toBeGreaterThan(0);
  });

  it('previews a buy at the ask plus the premium it pays for adding to the long skew', async () => {
    await render();
    await typeSize('2');
    // index ask 101 / 1.0005; skew 0.5 -> 2.5 pays (0.0005 + 0.0025) / 2 = 0.15%
    expect(text('[data-testid="fill-price"]')).toContain('$101.10');
    expect(text('[data-testid="order-value"]')).toContain('$202.20');
    // default leverage 10x
    expect(text('[data-testid="margin-required"]')).toContain('$20.22');
  });

  it('submits a market buy with an idempotency key and refreshes the account', async () => {
    await render();
    await typeSize('0.5');
    byTestId('submit-order').click();
    await settle();

    expect(api.submit).toHaveBeenCalledWith(
      { symbol: 'BTC-PERP', side: 'BUY', size: 0.5, leverage: 10, reduceOnly: false },
      expect.any(String),
    );
    // on open, when the session started, and after the fill
    expect(api.account).toHaveBeenCalledTimes(3);
  });

  it('switches to Sell / Short', async () => {
    await render();
    byTestId('side-sell').click();
    await typeSize('1');
    expect(byTestId('submit-order').textContent).toContain('Sell / Short');
    byTestId('submit-order').click();
    await settle();
    expect(api.submit.mock.calls[0][0]).toMatchObject({ side: 'SELL' });
  });

  it('shows the rejection reason returned by the API', async () => {
    await render();
    api.submit.mockReturnValueOnce(throwError(() => new HttpErrorResponse({
      status: 409, error: { error: { code: 'ORDER_REJECTED', message: 'Available collateral cannot cover the required margin and fees' } },
    })));
    await typeSize('500');
    byTestId('submit-order').click();
    await settle();
    expect(text('[data-testid="order-error"]')).toContain('Available collateral cannot cover');
  });

  it('reuses the idempotency key when retrying the same order after a network failure', async () => {
    await render();
    api.submit.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 0 })));
    await typeSize('1');
    byTestId('submit-order').click();
    await settle();
    byTestId('submit-order').click();
    await settle();
    expect(api.submit.mock.calls[1][1]).toBe(api.submit.mock.calls[0][1]);
  });

  it('lists open positions and closes one with a reduce-only market order', async () => {
    await render(longAccount);
    expect(text('[data-testid="position-row"]')).toContain('BTC-PERP');
    expect(text('[data-testid="position-row"]')).toContain('Long');
    byTestId('close-position').click();
    await settle();
    expect(api.submit).toHaveBeenCalledWith(
      { symbol: 'BTC-PERP', side: 'SELL', size: 2, leverage: 5, reduceOnly: true },
      expect.any(String),
    );
  });

  it('re-marks positions, PnL and account value on every streamed price', async () => {
    await render(longAccount);
    push([{ ...ticker, markPrice: 110 }]);
    await settle();
    // (110 - 101) * 2
    expect(text('[data-testid="position-row"]')).toContain('+$18.00');
    expect(text('[data-testid="position-row"]')).toContain('$220.00');
    expect(text('[data-testid="unrealized-pnl"]')).toBe('+$18.00');
    // 1010 collateral + 20.2 margin + 18 PnL
    expect(text('[data-testid="account-value"]')).toBe('$1,048.20');

    push([{ ...ticker, markPrice: 99 }]);
    await settle();
    expect(text('[data-testid="unrealized-pnl"]')).toBe('−$4.00');
  });

  it('estimates the next funding payment from the streamed rate', async () => {
    await render(longAccount);
    push([{ ...ticker, markPrice: 100, fundingRate: 0.001 }]);
    await settle();
    // the long pays 2 * 100 * 0.1%
    expect(text('[data-testid="next-funding"]')).toBe('−$0.20');
  });

  it('locks leverage to the open position so adds are not rejected', async () => {
    await render(longAccount);
    expect(text('[data-testid="leverage"]')).toContain('5x');
  });

  it('sizes the order from a share of buying power, allowing for the premium a large order pays', async () => {
    await render();
    (el().querySelector('[data-testid="quick-50"]') as HTMLElement).click();
    await settle();
    // ~99.55 BTC at the touch would move the premium to its 1% cap, so the max is sized at that fill (~99.08)
    expect(Number((byTestId('size-input') as HTMLInputElement).value)).toBeCloseTo(49.54, 1);
  });

  it('keeps the newest account when an older refresh responds last', async () => {
    await render();
    const older = new Subject<PerpAccount>();
    const newer = new Subject<PerpAccount>();
    api.account.mockReturnValueOnce(older).mockReturnValueOnce(newer);
    fixture.componentInstance.refresh();
    fixture.componentInstance.refresh();

    newer.next(longAccount);
    older.next(flatAccount);
    await settle();

    expect(fixture.componentInstance.account()).toBe(longAccount);
  });
});
