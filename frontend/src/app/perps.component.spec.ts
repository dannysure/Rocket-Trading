import { TestBed, ComponentFixture } from '@angular/core/testing';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';
import { PerpsComponent, PERPS_POLL_MS } from './perps.component';
import { PerpAccount, PerpsApi, PerpTicker } from './perps.service';

const ticker: PerpTicker = {
  symbol: 'BTC-PERP',
  markPrice: 100.5,
  oraclePrice: 100.4,
  bid: 100,
  ask: 101,
  fundingRate: 0.0000125,
  nextFundingAt: new Date(Date.now() + 12 * 60_000).toISOString(),
  openInterest: 3.5,
  maxLeverage: 40,
  takerFeeRate: 0.00045,
  maintenanceMarginRate: 0.0125,
  capturedAt: new Date().toISOString(),
  delayed: false,
};

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

function createApi(account: PerpAccount = flatAccount) {
  return {
    markets: vi.fn(() => of([
      { symbol: 'BTC-PERP', baseAsset: 'BTC', underlyingSymbol: 'X:BTC-USD', quoteCurrency: 'USD', maxLeverage: 40, takerFeeRate: 0.00045, maintenanceMarginRate: 0.0125 },
      { symbol: 'ETH-PERP', baseAsset: 'ETH', underlyingSymbol: 'X:ETH-USD', quoteCurrency: 'USD', maxLeverage: 25, takerFeeRate: 0.00045, maintenanceMarginRate: 0.02 },
    ])),
    ticker: vi.fn((_symbol: string) => of(ticker)),
    account: vi.fn(() => of<PerpAccount>(account)),
    orders: vi.fn(() => of([])),
    funding: vi.fn(() => of([])),
    submit: vi.fn((_request: unknown, _key: string) => of<any>({ orderId: 1, status: 'FILLED', filledSize: 1, fillPrice: 101 })),
  };
}

describe('PerpsComponent', () => {
  let fixture: ComponentFixture<PerpsComponent>;
  let api: ReturnType<typeof createApi>;

  async function render(account: PerpAccount = flatAccount, startLive = true, pollMs = 0) {
    api = createApi(account);
    await TestBed.configureTestingModule({
      imports: [PerpsComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: PerpsApi, useValue: api },
        { provide: PERPS_POLL_MS, useValue: pollMs },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(PerpsComponent);
    fixture.detectChanges();
    if (startLive) fixture.componentInstance.startLiveUpdates();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  const el = () => fixture.nativeElement as HTMLElement;
  const text = (selector: string) => el().querySelector(selector)?.textContent?.replace(/\s+/g, ' ').trim() ?? '';
  const byTestId = (id: string) => el().querySelector(`[data-testid="${id}"]`) as HTMLElement;

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

  it('starts dormant without quote calls until the button is clicked', async () => {
    await render(flatAccount, false);
    expect(text('[data-testid="live-status"]')).toContain('Dormant');
    expect(api.ticker).not.toHaveBeenCalled();
    expect(api.account).not.toHaveBeenCalled();
    fixture.componentInstance.refresh();
    fixture.componentInstance.selectMarket('ETH-PERP');
    expect(api.ticker).not.toHaveBeenCalled();
    byTestId('live-toggle').click();
    await settle();
    expect(api.ticker).toHaveBeenCalledWith('ETH-PERP');
    expect(api.account).toHaveBeenCalledTimes(1);
    expect(text('[data-testid="live-status"]')).toContain('10:00');
  });

  it('expires after ten minutes, cancels in-flight updates, and can restart', async () => {
    await render(flatAccount, false);
    vi.useFakeTimers();
    fixture.componentInstance.startLiveUpdates();
    const pending = new Subject<PerpAccount>();
    api.account.mockReturnValueOnce(pending);
    fixture.componentInstance.refresh();
    vi.advanceTimersByTime(10 * 60_000);
    expect(fixture.componentInstance.liveActive()).toBe(false);
    pending.next(longAccount);
    expect(fixture.componentInstance.account()).toBe(flatAccount);
    const calls = api.ticker.mock.calls.length;
    fixture.componentInstance.refresh();
    fixture.componentInstance.selectMarket('ETH-PERP');
    expect(api.ticker).toHaveBeenCalledTimes(calls);
    fixture.componentInstance.startLiveUpdates();
    expect(fixture.componentInstance.liveActive()).toBe(true);
    expect(api.ticker).toHaveBeenCalledTimes(calls + 1);
    fixture.componentInstance.stopLiveUpdates();
    expect(fixture.componentInstance.liveActive()).toBe(false);
  });

  it('polls only during the session and stops on expiry or navigation', async () => {
    await render(flatAccount, false, 3000);
    vi.useFakeTimers();
    fixture.componentInstance.startLiveUpdates();
    vi.advanceTimersByTime(6000);
    expect(api.ticker).toHaveBeenCalledTimes(3);
    vi.advanceTimersByTime(594000);
    expect(fixture.componentInstance.liveActive()).toBe(false);
    const calls = api.ticker.mock.calls.length;
    vi.advanceTimersByTime(60000);
    expect(api.ticker).toHaveBeenCalledTimes(calls);
    fixture.componentInstance.startLiveUpdates();
    fixture.destroy();
    const afterDestroy = api.ticker.mock.calls.length;
    vi.advanceTimersByTime(6000);
    expect(api.ticker).toHaveBeenCalledTimes(afterDestroy);
  });

  it('uses the shared navigation header', async () => {
    await render();
    expect(el().querySelector('app-nav-header')).toBeTruthy();
  });

  it('shows the market header with mark, oracle, funding and open interest', async () => {
    await render();
    expect(text('[data-testid="market-symbol"]')).toContain('BTC-PERP');
    expect(text('[data-testid="mark-price"]')).toContain('100.5');
    expect(text('[data-testid="oracle-price"]')).toContain('100.4');
    expect(text('[data-testid="funding-rate"]')).toContain('0.0013%');
    expect(text('[data-testid="funding-countdown"]')).toMatch(/00:1[12]:\d\d/);
    expect(text('[data-testid="open-interest"]')).toContain('3.5');
  });

  it('switches markets from the market pills', async () => {
    await render();
    (el().querySelector('[data-testid="market-ETH-PERP"]') as HTMLElement).click();
    await settle();
    expect(api.ticker).toHaveBeenLastCalledWith('ETH-PERP');
  });

  it('labels the order book as simulated depth around the live quote', async () => {
    await render();
    expect(text('[data-testid="order-book"]')).toContain('Simulated depth');
    expect(el().querySelectorAll('[data-testid="ask-level"]').length).toBeGreaterThan(0);
    expect(el().querySelectorAll('[data-testid="bid-level"]').length).toBeGreaterThan(0);
  });

  it('previews order value and margin from the ask for a buy', async () => {
    await render();
    await typeSize('2');
    expect(text('[data-testid="order-value"]')).toContain('$202.00');
    // default leverage 10x -> $20.20 margin
    expect(text('[data-testid="margin-required"]')).toContain('$20.20');
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
    expect(api.account).toHaveBeenCalledTimes(2);
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

  it('locks leverage to the open position so adds are not rejected', async () => {
    await render(longAccount);
    expect(text('[data-testid="leverage"]')).toContain('5x');
  });

  it('sizes the order from a share of buying power', async () => {
    await render();
    (el().querySelector('[data-testid="quick-50"]') as HTMLElement).click();
    await settle();
    // max = 1010 / (101/10 + 101*0.00045) = 99.55...; half is ~49.77
    expect(Number((byTestId('size-input') as HTMLInputElement).value)).toBeCloseTo(49.77, 1);
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
