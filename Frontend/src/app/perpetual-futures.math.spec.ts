import {
  DEFAULT_PERPETUAL_MARKET,
  buildFundingSnapshot,
  calculateFundingCashflowUsd,
  calculateLiquidationPrice,
  calculatePositionSummary,
  createReferenceQuote,
} from './perpetual-futures.math';

describe('perpetual futures funding math', () => {
  it('raises funding when the modeled perp trades above spot', () => {
    const quote = createReferenceQuote(DEFAULT_PERPETUAL_MARKET);
    const snapshot = buildFundingSnapshot(DEFAULT_PERPETUAL_MARKET, quote, {
      basisAdjustmentBps: 10,
      inventorySkewBps: 8,
    });

    expect(snapshot.markPrice).toBeGreaterThan(snapshot.indexPrice);
    expect(snapshot.fundingRate).toBeGreaterThan(0);
    expect(snapshot.fundingDirection).toBe('longs-pay-shorts');
    expect(snapshot.annualizedFundingRate).toBeGreaterThan(snapshot.fundingRate);
  });

  it('flips funding negative when the modeled perp trades below spot', () => {
    const quote = createReferenceQuote(DEFAULT_PERPETUAL_MARKET);
    const snapshot = buildFundingSnapshot(DEFAULT_PERPETUAL_MARKET, quote, {
      basisAdjustmentBps: -20,
      inventorySkewBps: -12,
    });

    expect(snapshot.markPrice).toBeLessThan(snapshot.indexPrice);
    expect(snapshot.fundingRate).toBeLessThan(0);
    expect(snapshot.fundingDirection).toBe('shorts-pay-longs');
  });

  it('keeps funding cashflows and liquidation math consistent for a long position', () => {
    const quote = createReferenceQuote(DEFAULT_PERPETUAL_MARKET);
    const snapshot = buildFundingSnapshot(DEFAULT_PERPETUAL_MARKET, quote, {
      basisAdjustmentBps: 12,
      inventorySkewBps: 6,
    });
    const position = calculatePositionSummary(
      snapshot,
      DEFAULT_PERPETUAL_MARKET,
      'long',
      3,
      10,
    );

    expect(position.notionalUsd).toBeGreaterThan(0);
    expect(position.initialMarginUsd).toBeCloseTo(position.notionalUsd / 10, 6);
    expect(position.maintenanceMarginUsd).toBeCloseTo(
      position.notionalUsd * position.maintenanceMarginRate,
      6,
    );
    expect(position.liquidationPrice).toBeLessThan(snapshot.markPrice);
    expect(position.nextFundingCashflowUsd).toBeCloseTo(
      calculateFundingCashflowUsd(position.notionalUsd, snapshot.fundingRate, 'long'),
      6,
    );
    expect(position.liquidationPrice).toBeCloseTo(
      calculateLiquidationPrice(
        snapshot.markPrice,
        10,
        position.maintenanceMarginRate,
        'long',
      ),
      6,
    );
  });
});
