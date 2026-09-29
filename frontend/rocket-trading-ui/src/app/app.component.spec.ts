import { TestBed, fakeAsync, flushMicrotasks, tick } from '@angular/core/testing';
import { of, throwError, Subject } from 'rxjs';
import { AppComponent } from './app.component';
import { ApiService } from './api.service';

const session = { clientId: 1, sessionId: 2, accessToken: 'token', tokenType: 'Bearer', expiresAt: '2099-01-01T00:00:00Z' };
const envelope = (data: unknown) => ({ data, meta: { requestId: 'test', timestamp: '2026-01-01T00:00:00Z' } });
const order = { orderId: 1, symbol: 'AAPL', side: 'BUY', quantity: 1, orderType: 'MARKET', status: 'ACCEPTED', submittedAt: '2026-01-01T00:00:00Z' };

describe('Trading dashboard', () => {
  let api: jasmine.SpyObj<ApiService>;
  beforeEach(async () => {
    localStorage.clear();
    api = jasmine.createSpyObj('ApiService', ['register', 'signIn', 'signOut', 'getPortfolio', 'listOrders', 'listFills', 'submitOrder']);
    api.getPortfolio.and.returnValue(of(envelope({ clientId: 1, accountId: 1, cashBalance: 10000, currency: 'USD', positions: [] })) as any);
    api.listOrders.and.returnValue(of(envelope([])) as any);
    api.listFills.and.returnValue(of(envelope([{ fillId: 1, orderId: 1, executedQuantity: 1, executedPrice: 101, executedAt: '2026-01-01T00:00:00Z' }])) as any);
    api.signOut.and.returnValue(of(undefined));
    await TestBed.configureTestingModule({ imports: [AppComponent], providers: [{ provide: ApiService, useValue: api }] }).compileComponents();
  });
  afterEach(() => localStorage.clear());

  it('starts signed out', () => {
    const fixture = TestBed.createComponent(AppComponent);
    expect(fixture.componentInstance.session).toBeNull();
    fixture.destroy();
  });

  it('polls accepted orders, loads fills, and stops after settlement', fakeAsync(() => {
    api.listOrders.and.returnValues(of(envelope([order])) as any, of(envelope([{ ...order, status: 'FILLED' }])) as any);
    const fixture = TestBed.createComponent(AppComponent);
    const component = fixture.componentInstance;
    component.session = session;
    void component.refreshDashboard(); flushMicrotasks();
    expect(component.orders[0].status).toBe('ACCEPTED');
    tick(2000); flushMicrotasks();
    expect(component.orders[0].status).toBe('FILLED');
    expect(component.fills[1][0].executedPrice).toBe(101);
    tick(4000); flushMicrotasks();
    expect(api.listOrders).toHaveBeenCalledTimes(2);
    fixture.destroy();
  }));

  it('reads balances after observing a completed order', fakeAsync(() => {
    const orders = new Subject<any>();
    api.listOrders.and.returnValue(orders);
    const fixture = TestBed.createComponent(AppComponent);
    const component = fixture.componentInstance;
    component.session = session;
    void component.refreshDashboard(); flushMicrotasks();
    expect(api.getPortfolio).not.toHaveBeenCalled();
    orders.next(envelope([{ ...order, status: 'FILLED' }]));
    flushMicrotasks();
    expect(api.getPortfolio).toHaveBeenCalledTimes(1);
    expect(component.orders[0].status).toBe('FILLED');
    fixture.destroy();
  }));

  it('stops polling and clears client data on sign-out', fakeAsync(() => {
    api.listOrders.and.returnValue(of(envelope([order])) as any);
    const fixture = TestBed.createComponent(AppComponent);
    const component = fixture.componentInstance;
    component.session = session;
    void component.refreshDashboard(); flushMicrotasks();
    void component.signOut(); flushMicrotasks();
    tick(4000); flushMicrotasks();
    expect(api.listOrders).toHaveBeenCalledTimes(1);
    expect(component.orders).toEqual([]);
    expect(component.portfolio).toBeNull();
    fixture.destroy();
  }));

  it('reuses the request key after an uncertain network response', async () => {
    api.submitOrder.and.returnValues(throwError(() => ({ status: 0, message: 'Network interrupted' })), of(envelope(order)) as any);
    const fixture = TestBed.createComponent(AppComponent);
    const component = fixture.componentInstance;
    component.session = session;
    await component.submitOrder();
    await component.submitOrder();
    expect(api.submitOrder.calls.argsFor(0)[1]).toBe(api.submitOrder.calls.argsFor(1)[1]);
    expect(localStorage.getItem('rocketTradingAttempt')).toBeNull();
    fixture.destroy();
  });

  it('clears restored session when the API rejects it', async () => {
    api.getPortfolio.and.returnValue(throwError(() => ({ status: 401 })));
    const fixture = TestBed.createComponent(AppComponent);
    fixture.componentInstance.session = session;
    await fixture.componentInstance.refreshDashboard();
    expect(fixture.componentInstance.session).toBeNull();
    fixture.destroy();
  });
});
