import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { App } from './app';
import { routes } from './app.routes';
import { SessionStore } from './api.service';
import { dateOfBirthProblem } from './complete-profile.component';
import { describeAuditEvent, displaySymbol, money, orderStatus } from './format';
import { NavHeaderComponent } from './nav-header.component';
import { PerpsComponent } from './perps.component';

describe('App', () => {
  beforeEach(async () => {
    sessionStorage.clear();
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter(routes), provideHttpClient()],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('sends each role to its own home', () => {
    const sessions = TestBed.inject(SessionStore);
    const base = { clientId: 1, name: 'Test', email: 't@example.com', token: 't', expiresAt: new Date(Date.now() + 60_000).toISOString() };
    sessions.set({ ...base, role: 'CLIENT' });
    expect(sessions.homeRoute()).toBe('/dashboard');
    sessions.set({ ...base, role: 'ANALYST' });
    expect(sessions.homeRoute()).toBe('/insights');
    expect(sessions.isStaff()).toBe(true);
    sessions.set({ ...base, role: 'OPERATIONS' });
    expect(sessions.homeRoute()).toBe('/ops');
  });

  it('serves the perpetuals page to clients behind the client guard', () => {
    const route = routes.find((r) => r.path === 'perps');
    expect(route?.component).toBe(PerpsComponent);
    expect(route?.canActivate?.length).toBe(1);
  });

  it('links clients to the perpetuals page from the navigation header', () => {
    TestBed.inject(SessionStore).set({ clientId: 1, name: 'Test', email: 't@example.com', token: 't', role: 'CLIENT', expiresAt: new Date(Date.now() + 60_000).toISOString() });
    const fixture = TestBed.createComponent(NavHeaderComponent);
    fixture.detectChanges();
    const link = (fixture.nativeElement as HTMLElement).querySelector('a[href="/perps"]');
    expect(link?.textContent).toContain('Perps');
  });

  it('treats an expired session as signed out', () => {
    const sessions = TestBed.inject(SessionStore);
    sessions.set({ clientId: 1, name: 'T', email: 't@example.com', token: 't', role: 'CLIENT', expiresAt: new Date(Date.now() - 1000).toISOString() });
    expect(sessions.isActive()).toBe(false);
  });
});

describe('format', () => {
  it('formats money in the right currency', () => {
    expect(money(1824.4, 'USD')).toBe('$1,824.40');
    expect(money(1166.7, 'INR')).toBe('₹1,166.70');
    expect(money(null, 'USD')).toBe('—');
  });

  it('shows symbols the way people say them', () => {
    expect(displaySymbol('X:BTC-USD')).toBe('BTC');
    expect(displaySymbol('FX:EURUSD')).toBe('EUR/USD');
    expect(displaySymbol('RELIANCE.NS')).toBe('RELIANCE.NS');
  });

  it('says an accepted order waiting for its market is waiting, not just accepted', () => {
    expect(orderStatus({ status: 'ACCEPTED', awaitingMarket: true }).label).toBe('Waiting for market');
    expect(orderStatus({ status: 'ACCEPTED', awaitingMarket: false }).label).toBe('Accepted');
    expect(orderStatus({ status: 'FILLED', awaitingMarket: false }).open).toBe(false);
  });

  it('describes audit records in plain words', () => {
    const priced = describeAuditEvent({
      auditId: 1, entityName: 'orders', entityId: 9, actionType: 'INSERT', recordedAt: '2026-10-13T10:00:00Z', stateBefore: null,
      stateAfter: { stage: 'execution', bid: 1000, ask: 1001, capturedAt: '2026-10-13T09:58:00Z', instrumentCurrency: 'INR',
        fxSymbol: 'FX:INRUSD', fxRate: 0.0121, settlementAmount: 24.2242, settlementCurrency: 'USD', current: true },
    });
    expect(priced.title).toBe('Priced for execution');
    expect(priced.detail).toContain('INR/USD at 0.0121');
    expect(priced.detail).toContain('$24.22');

    const rejected = describeAuditEvent({
      auditId: 2, entityName: 'orders', entityId: 9, actionType: 'UPDATE', recordedAt: '2026-10-13T10:00:01Z',
      stateBefore: { status: 'SUBMITTED' }, stateAfter: { status: 'REJECTED', reason: 'Available cash cannot cover the requested order' },
    });
    expect(rejected.title).toBe('Status SUBMITTED → REJECTED');
    expect(rejected.detail).toContain('Available cash');
  });

  it('accepts a date of birth only for real dates of people 18 or over', () => {
    const today = new Date(2026, 9, 13);
    expect(dateOfBirthProblem('2008-10-13', today)).toBeNull();
    expect(dateOfBirthProblem('1950-01-01', today)).toBeNull();
    expect(dateOfBirthProblem('2008-10-14', today)).toContain('at least 18');
    expect(dateOfBirthProblem('2027-01-01', today)).toContain('future');
    expect(dateOfBirthProblem('2001-02-30', today)).toContain('valid');
    expect(dateOfBirthProblem('1800-01-01', today)).toContain('valid');
    expect(dateOfBirthProblem('20255-01-01', today)).toContain('valid');
    expect(dateOfBirthProblem('', today)).toContain('Enter your date of birth');
  });
});
