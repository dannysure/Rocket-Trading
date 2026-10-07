import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { PerpetualFuturesPageComponent } from './perpetual-futures-page.component';
import { PerpetualFuturesService } from './perpetual-futures.service';

describe('PerpetualFuturesPageComponent', () => {
  function createMockService() {
    return {
      getAccounts: () =>
        of([
          {
            accountId: 7,
            clientId: 14,
            clientName: 'Ada Lovelace',
            accountType: 'DIRECT_TRADING',
            currency: 'USD',
            cashBalance: 125000,
          },
        ]),
      getMarketOverview: () =>
        of({
          marketSymbol: 'ETH-PERP',
          displayName: 'Ethereum Perpetual Market',
          assetSymbol: 'ETH',
          quoteCurrency: 'USD',
          settlementAsset: 'USDC',
          spotSymbol: 'ETH-USD',
          tradingViewSymbol: 'BITSTAMP:ETHUSD',
          indexPrice: 3520,
          markPrice: 3524,
          bestBid: 3523.5,
          bestAsk: 3524.5,
          basisBps: 11,
          premiumIndex: 0.0011,
          fundingRate: 0.001,
          annualizedFundingRate: 0.11,
          fundingDirection: 'longs-pay-shorts',
          nextFundingAt: new Date().toISOString(),
          openInterestUsd: 480000000,
          longOpenInterestUsd: 251000000,
          shortOpenInterestUsd: 229000000,
          longShortRatio: 1.1,
          volume24hUsd: 1950000000,
          insuranceFundUsd: 38500000,
          spotChange24hPct: 1.8,
          quoteTimestamp: new Date().toISOString(),
          accountId: 7,
          clientId: 14,
          availableCollateralUsd: 122500,
          accountEquityUsd: 126200,
          maintenanceMarginRate: 0.005,
          maxLeverage: 50,
          makerFeeRate: 0.0002,
          takerFeeRate: 0.00055,
          position: null,
          recentOrders: [],
        }),
      submitOrder: () =>
        of({
          order: {
            orderId: 99,
            marketSymbol: 'ETH-PERP',
            side: 'BUY',
            orderType: 'MARKET',
            reduceOnly: false,
            quantity: 0.5,
            leverage: 5,
            limitPrice: null,
            status: 'FILLED',
            markPrice: 3524,
            averageFillPrice: 3524.5,
            notionalUsd: 1762.25,
            feeUsd: 0.97,
            rejectionReason: null,
            createdAt: new Date().toISOString(),
            filledAt: new Date().toISOString(),
            fill: null,
          },
        }),
    };
  }

  it('renders the database-backed ETH perp trading layout', async () => {
    await TestBed.configureTestingModule({
      imports: [PerpetualFuturesPageComponent],
      providers: [{ provide: PerpetualFuturesService, useValue: createMockService() }],
    }).compileComponents();

    const fixture = TestBed.createComponent(PerpetualFuturesPageComponent);
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('ETH-PERP');
    expect(compiled.textContent).toContain('Place order');
    expect(compiled.textContent).toContain('Ada Lovelace');
    expect(compiled.textContent).toContain('Open position');
  });

  it('shows the account loading failure from the backend', async () => {
    await TestBed.configureTestingModule({
      imports: [PerpetualFuturesPageComponent],
      providers: [
        {
          provide: PerpetualFuturesService,
          useValue: {
            getAccounts: () => throwError(() => ({ error: { detail: 'accounts unavailable' } })),
            getMarketOverview: () => of(null),
            submitOrder: () => of(null),
          },
        },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(PerpetualFuturesPageComponent);
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('accounts unavailable');
  });
});
