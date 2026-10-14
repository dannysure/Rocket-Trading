import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { API_BASE_URL } from './api.service';
import { PerpsApi } from './perps.service';

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

  it('url-encodes the market symbol for the ticker', () => {
    perps.ticker('BTC-PERP').subscribe();
    http.expectOne(`${api}/markets/BTC-PERP/ticker`).flush({ data: null });
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
