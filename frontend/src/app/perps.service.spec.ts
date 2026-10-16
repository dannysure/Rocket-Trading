import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { API_BASE_URL, SessionStore } from './api.service';
import { PerpsApi, PerpsStream, PerpStreamError, ServerSentEvents } from './perps.service';

describe('PerpsApi', () => {
  const api = `${API_BASE_URL}/perps`;
  let perps: PerpsApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    perps = TestBed.inject(PerpsApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('unwraps the data envelope for markets', () => {
    let markets: unknown;
    perps.markets().subscribe((data) => (markets = data));
    http.expectOne(`${api}/markets`).flush({ data: [{ symbol: 'BTC-PERP' }] });
    expect(markets).toEqual([{ symbol: 'BTC-PERP' }]);
  });

  it('requests candles for a market and interval', () => {
    let candles: unknown;
    perps.candles('BTC-PERP', 300).subscribe((data) => (candles = data));
    http.expectOne(`${api}/markets/BTC-PERP/candles?interval=300`).flush({ data: [{ time: 1, open: 1, high: 2, low: 1, close: 2 }] });
    expect(candles).toEqual([{ time: 1, open: 1, high: 2, low: 1, close: 2 }]);
  });

  it('submits orders with an Idempotency-Key so retries cannot fill twice', () => {
    const order = { symbol: 'BTC-PERP', side: 'BUY' as const, size: 0.5, leverage: 10, reduceOnly: false };
    perps.submit(order, 'key-1').subscribe();
    const request = http.expectOne(`${api}/orders`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(order);
    expect(request.request.headers.get('Idempotency-Key')).toBe('key-1');
    request.flush({ data: null });
  });

  it('reads the account, order history and funding history', () => {
    perps.account().subscribe();
    perps.orders().subscribe();
    perps.funding().subscribe();
    http.expectOne(`${api}/account`).flush({ data: null });
    http.expectOne(`${api}/orders`).flush({ data: [] });
    http.expectOne(`${api}/funding`).flush({ data: [] });
  });
});

describe('ServerSentEvents', () => {
  it('emits each event\'s data, however the body is split into chunks', () => {
    const received: string[] = [];
    const events = new ServerSentEvents((data) => received.push(data));
    events.push('event:tickers\ndata:[{"symbol":"BTC');
    expect(received).toEqual([]);
    events.push('-PERP"}]\n\nevent:tickers\r\ndata: [2]\r\n\r\n:comment\n\n');
    expect(received).toEqual(['[{"symbol":"BTC-PERP"}]', '[2]']);
  });
});

describe('PerpsStream', () => {
  const encoder = new TextEncoder();
  let fetchMock: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    TestBed.inject(SessionStore).set({ token: 'token-1', role: 'CLIENT' } as never);
    fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    TestBed.inject(SessionStore).clear();
  });

  function body(...chunks: string[]) {
    return new ReadableStream<Uint8Array>({
      start(controller) {
        for (const chunk of chunks) controller.enqueue(encoder.encode(chunk));
        controller.close();
      },
    });
  }

  it('sends the bearer token and emits each pushed ticker snapshot', async () => {
    fetchMock.mockResolvedValue(new Response(body('event:tickers\ndata:[{"symbol":"BTC-PERP"}]\n\n', 'event:tickers\ndata:[{"symbol":"ETH-PERP"}]\n\n'), { status: 200 }));
    const snapshots: unknown[] = [];
    const ended = new Promise<unknown>((resolve) => TestBed.inject(PerpsStream).tickers().subscribe({ next: (t) => snapshots.push(t), error: resolve }));

    const error = await ended;
    expect(fetchMock.mock.calls[0][0]).toBe(`${API_BASE_URL}/perps/stream`);
    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBe('Bearer token-1');
    expect(snapshots).toEqual([[{ symbol: 'BTC-PERP' }], [{ symbol: 'ETH-PERP' }]]);
    // The server closing the stream is reported so the caller can reconnect
    expect(error).toEqual(new PerpStreamError(0));
  });

  it('reports the HTTP status when the stream is refused', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 401 }));
    const error = await new Promise<PerpStreamError>((resolve) => TestBed.inject(PerpsStream).tickers().subscribe({ error: resolve }));
    expect(error.status).toBe(401);
  });

  it('aborts the request when unsubscribed', () => {
    fetchMock.mockReturnValue(new Promise(() => undefined));
    const subscription = TestBed.inject(PerpsStream).tickers().subscribe();
    const signal: AbortSignal = fetchMock.mock.calls[0][1].signal;
    expect(signal.aborted).toBe(false);
    subscription.unsubscribe();
    expect(signal.aborted).toBe(true);
  });
});
