import {
  buildDepthLadder,
  formatCountdown,
  liquidationPrice,
  maxOrderSize,
  previewOrder,
  toCandles,
} from './perps.math';

describe('perps math', () => {
  describe('liquidationPrice', () => {
    it('matches the backend formula for longs and shorts', () => {
      expect(liquidationPrice('LONG', 100, 1, 10, 0.025)).toBeCloseTo(92.3077, 4);
      expect(liquidationPrice('SHORT', 100, 1, 10, 0.025)).toBeCloseTo(107.3171, 4);
    });

    it('is null for a fully collateralised long', () => {
      expect(liquidationPrice('LONG', 100, 1, 100, 0.025)).toBeNull();
    });
  });

  describe('previewOrder', () => {
    const market = { takerFeeRate: 0.00045, maintenanceMarginRate: 0.025 };

    it('prices a new position: value, margin, fee and liquidation price', () => {
      const preview = previewOrder({ ...market, side: 'BUY', size: 2, price: 100, leverage: 10, position: null });
      expect(preview.orderValue).toBe(200);
      expect(preview.marginRequired).toBe(20);
      expect(preview.fee).toBeCloseTo(0.09, 6);
      expect(preview.liquidationPrice).toBeCloseTo(92.3077, 4);
    });

    it('uses the combined position when adding to it', () => {
      const position = { side: 'LONG' as const, size: 2, entryPrice: 100, margin: 20 };
      const preview = previewOrder({ ...market, side: 'BUY', size: 2, price: 110, leverage: 10, position });
      expect(preview.marginRequired).toBe(22);
      // size 4, entry 105, margin 42
      expect(preview.liquidationPrice).toBeCloseTo(liquidationPrice('LONG', 105, 4, 42, 0.025)!, 6);
    });

    it('needs no margin to reduce, and shows no liquidation price once flat', () => {
      const position = { side: 'LONG' as const, size: 2, entryPrice: 100, margin: 20 };
      const preview = previewOrder({ ...market, side: 'SELL', size: 2, price: 110, leverage: 10, position });
      expect(preview.marginRequired).toBe(0);
      expect(preview.liquidationPrice).toBeNull();
    });

    it('only margins the remainder when an order flips the position', () => {
      const position = { side: 'LONG' as const, size: 1, entryPrice: 100, margin: 10 };
      const preview = previewOrder({ ...market, side: 'SELL', size: 3, price: 110, leverage: 10, position });
      expect(preview.marginRequired).toBe(22);
      expect(preview.liquidationPrice).toBeCloseTo(liquidationPrice('SHORT', 110, 2, 22, 0.025)!, 6);
    });

    it('caps reduce-only orders at the open size', () => {
      const position = { side: 'SHORT' as const, size: 1, entryPrice: 100, margin: 10 };
      const preview = previewOrder({ ...market, side: 'BUY', size: 5, price: 100, leverage: 10, position, reduceOnly: true });
      expect(preview.orderValue).toBe(100);
      expect(preview.marginRequired).toBe(0);
    });
  });

  it('maxOrderSize spends available collateral on margin plus fee', () => {
    // each unit costs 100/10 margin + 100 * 0.00045 fee = 10.045
    expect(maxOrderSize(1004.5, 100, 10, 0.00045)).toBeCloseTo(100, 6);
    expect(maxOrderSize(0, 100, 10, 0.00045)).toBe(0);
  });

  it('formatCountdown renders hh:mm:ss and never goes negative', () => {
    expect(formatCountdown(12 * 60_000 + 4_000)).toBe('00:12:04');
    expect(formatCountdown(-5)).toBe('00:00:00');
  });

  it('toCandles buckets price samples into OHLC candles', () => {
    const candles = toCandles(
      [
        { time: 0, price: 10 },
        { time: 30_000, price: 12 },
        { time: 59_000, price: 9 },
        { time: 60_000, price: 11 },
      ],
      60_000,
    );
    expect(candles).toEqual([
      { time: 0, open: 10, high: 12, low: 9, close: 9 },
      { time: 60_000, open: 11, high: 11, low: 11, close: 11 },
    ]);
  });

  describe('buildDepthLadder', () => {
    it('anchors the ladder on the live bid and ask with cumulative totals', () => {
      const ladder = buildDepthLadder(100, 101, 5);
      expect(ladder.asks).toHaveLength(5);
      expect(ladder.bids).toHaveLength(5);
      expect(ladder.asks[ladder.asks.length - 1].price).toBe(101);
      expect(ladder.bids[0].price).toBe(100);
      // asks listed high -> low, bids high -> low
      expect(ladder.asks[0].price).toBeGreaterThan(ladder.asks[1].price);
      expect(ladder.bids[0].price).toBeGreaterThan(ladder.bids[1].price);
      // totals accumulate away from the spread
      expect(ladder.bids[4].total).toBeCloseTo(ladder.bids.reduce((sum, level) => sum + level.size, 0), 6);
      expect(ladder.asks[0].total).toBeCloseTo(ladder.asks.reduce((sum, level) => sum + level.size, 0), 6);
      expect(ladder.spread).toBeCloseTo(1, 6);
    });

    it('is deterministic for the same quote so it does not flicker between polls', () => {
      expect(buildDepthLadder(100, 101, 5)).toEqual(buildDepthLadder(100, 101, 5));
    });
  });
});
