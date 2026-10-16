// Client-side previews for the perps order ticket. The backend is authoritative for fills;
// these mirror its isolated-margin formulas so the ticket can show estimates before submitting.

export type PerpSide = 'LONG' | 'SHORT';
export type OrderSide = 'BUY' | 'SELL';

export interface PreviewPosition {
  side: PerpSide;
  size: number;
  entryPrice: number;
  margin: number;
}

export interface OrderPreviewInput {
  side: OrderSide;
  size: number;
  price: number;
  leverage: number;
  takerFeeRate: number;
  maintenanceMarginRate: number;
  position: PreviewPosition | null;
  reduceOnly?: boolean;
}

export interface OrderPreview {
  orderValue: number;
  marginRequired: number;
  fee: number;
  liquidationPrice: number | null;
}

export interface DepthLevel {
  price: number;
  size: number;
  total: number;
}

export interface DepthLadder {
  asks: DepthLevel[];
  bids: DepthLevel[];
  spread: number;
  spreadPercent: number;
  tick: number;
}

/** time is the interval start in Unix seconds. */
export interface Candle {
  time: number;
  open: number;
  high: number;
  low: number;
  close: number;
}

/** Mark price at which equity equals maintenance margin; null when no positive price triggers it. */
export function liquidationPrice(side: PerpSide, entry: number, size: number, margin: number, mmr: number): number | null {
  const numerator = side === 'LONG' ? entry * size - margin : entry * size + margin;
  const denominator = size * (side === 'LONG' ? 1 - mmr : 1 + mmr);
  if (numerator <= 0 || denominator <= 0) return null;
  return numerator / denominator;
}

export function previewOrder(input: OrderPreviewInput): OrderPreview {
  const { price, leverage, takerFeeRate: feeRate, maintenanceMarginRate: mmr, position } = input;
  const side: PerpSide = input.side === 'BUY' ? 'LONG' : 'SHORT';
  const increasing = !position || position.side === side;
  const size = input.reduceOnly && position && !increasing ? Math.min(input.size, position.size) : input.size;
  const orderValue = size * price;
  const fee = orderValue * feeRate;

  if (increasing) {
    const marginRequired = orderValue / leverage;
    const totalSize = (position?.size ?? 0) + size;
    const entry = ((position?.entryPrice ?? 0) * (position?.size ?? 0) + price * size) / totalSize;
    const margin = (position?.margin ?? 0) + marginRequired;
    return { orderValue, marginRequired, fee, liquidationPrice: liquidationPrice(side, entry, totalSize, margin, mmr) };
  }

  const closing = Math.min(size, position!.size);
  const remainder = size - closing;
  if (remainder > 0) {
    const marginRequired = (remainder * price) / leverage;
    return { orderValue, marginRequired, fee, liquidationPrice: liquidationPrice(side, price, remainder, marginRequired, mmr) };
  }
  const leftover = position!.size - closing;
  const liq = leftover > 0
    ? liquidationPrice(position!.side, position!.entryPrice, leftover, (position!.margin * leftover) / position!.size, mmr)
    : null;
  return { orderValue, marginRequired: 0, fee, liquidationPrice: liq };
}

/** Largest size whose margin plus taker fee fits in the available collateral. */
export function maxOrderSize(available: number, price: number, leverage: number, feeRate: number): number {
  if (available <= 0 || price <= 0 || leverage <= 0) return 0;
  return available / (price / leverage + price * feeRate);
}

export function formatCountdown(ms: number): string {
  const totalSeconds = Math.max(0, Math.floor(ms / 1000));
  const pad = (value: number) => String(value).padStart(2, '0');
  return `${pad(Math.floor(totalSeconds / 3600))}:${pad(Math.floor((totalSeconds % 3600) / 60))}:${pad(totalSeconds % 60)}`;
}

/**
 * Folds a live price into the newest candle: extends it within its interval, or opens the next one.
 * Returns null for a price older than that candle, which must not rewrite closed history.
 */
export function mergeTick(last: Candle | null, timeSeconds: number, price: number, intervalSeconds: number): Candle | null {
  const bucket = Math.floor(timeSeconds / intervalSeconds) * intervalSeconds;
  if (!last || bucket > last.time) return { time: bucket, open: price, high: price, low: price, close: price };
  if (bucket < last.time) return null;
  return { ...last, high: Math.max(last.high, price), low: Math.min(last.low, price), close: price };
}

export interface MarkedPosition {
  side: PerpSide;
  size: number;
  entryPrice: number;
  margin: number;
}

/** Position value, PnL and return on margin at a mark price, as the backend computes them. */
export function markToMarket(position: MarkedPosition, markPrice: number) {
  const direction = position.side === 'LONG' ? 1 : -1;
  const unrealizedPnl = (markPrice - position.entryPrice) * position.size * direction;
  return {
    markPrice,
    positionValue: markPrice * position.size,
    unrealizedPnl,
    returnOnEquity: position.margin > 0 ? unrealizedPnl / position.margin : null,
  };
}

export interface FillEstimateInput {
  side: OrderSide;
  // Size the order will actually fill (reduce-only orders are capped at the position)
  size: number;
  bid: number;
  ask: number;
  premium: number;
  skew: number;
  skewScale: number;
  maxPremium: number;
}

/**
 * Expected fill price, mirroring the backend: the index bid or ask plus the average premium over the
 * open-interest skew before and after the order. The ticker's bid/ask already carry the current premium.
 */
export function estimateFillPrice(input: FillEstimateInput): number {
  const { side, size, skew, skewScale, maxPremium } = input;
  const touch = side === 'BUY' ? input.ask : input.bid;
  if (skewScale <= 0) return touch;
  const premiumAt = (value: number) => Math.max(-maxPremium, Math.min(maxPremium, value / skewScale));
  const after = premiumAt(skew + (side === 'BUY' ? size : -size));
  return (touch / (1 + input.premium)) * (1 + (premiumAt(skew) + after) / 2);
}

/**
 * Illustrative depth around the live top of book. Orders fill against the quote, not this ladder,
 * so the UI labels it as simulated. Seeded by the quote so it stays stable between polls.
 */
export function buildDepthLadder(bid: number, ask: number, levels: number): DepthLadder {
  const tick = priceTick(bid);
  const decimals = Math.max(0, -Math.floor(Math.log10(tick)));
  const round = (value: number) => Number(value.toFixed(decimals));
  const random = seededRandom(Math.round(bid / tick));

  const bids: DepthLevel[] = [];
  let bidTotal = 0;
  for (let i = 0; i < levels; i++) {
    const size = levelSize(random(), i);
    bidTotal += size;
    bids.push({ price: round(bid - i * tick), size, total: bidTotal });
  }

  const asksNearFirst: DepthLevel[] = [];
  let askTotal = 0;
  for (let i = 0; i < levels; i++) {
    const size = levelSize(random(), i);
    askTotal += size;
    asksNearFirst.push({ price: round(ask + i * tick), size, total: askTotal });
  }

  const spread = ask - bid;
  return { asks: asksNearFirst.reverse(), bids, spread, spreadPercent: (spread / ask) * 100, tick };
}

function priceTick(price: number): number {
  if (price <= 0) return 0.0001;
  return 10 ** (Math.floor(Math.log10(price)) - 4);
}

function levelSize(random: number, depth: number): number {
  return Number((random * 8 * (1 + depth * 0.25) + 0.05).toFixed(4));
}

// mulberry32: tiny deterministic PRNG
function seededRandom(seed: number): () => number {
  let state = seed >>> 0;
  return () => {
    state = (state + 0x6d2b79f5) >>> 0;
    let t = state;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
