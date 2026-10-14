import { AuditEvent, Order, Segment } from './api.service';

const moneyFormats = new Map<string, Intl.NumberFormat>();

/** A money amount in its currency, e.g. $1,824.40 or ₹1,166.70. */
export function money(value: number | null | undefined, currency: string | null | undefined, digits = 2): string {
  if (value === null || value === undefined || Number.isNaN(Number(value))) return '—';
  const code = (currency || 'USD').toUpperCase();
  const key = `${code}|${digits}`;
  let format = moneyFormats.get(key);
  if (!format) {
    try {
      format = new Intl.NumberFormat('en-US', {
        style: 'currency', currency: code, minimumFractionDigits: digits, maximumFractionDigits: digits,
      });
    } catch {
      format = new Intl.NumberFormat('en-US', { minimumFractionDigits: digits, maximumFractionDigits: digits });
    }
    moneyFormats.set(key, format);
  }
  return format.format(Number(value));
}

/** A signed money change, e.g. +$12.40 / −$3.10. */
export function signedMoney(value: number | null | undefined, currency: string | null | undefined): string {
  if (value === null || value === undefined) return '—';
  const sign = value > 0 ? '+' : value < 0 ? '−' : '';
  return sign + money(Math.abs(value), currency);
}

/** A price with enough decimals for its size (FX rates need more than share prices). */
export function price(value: number | null | undefined, currency: string | null | undefined): string {
  if (value === null || value === undefined) return '—';
  const abs = Math.abs(Number(value));
  const digits = abs > 0 && abs < 10 ? 4 : 2;
  return money(value, currency, digits);
}

export function quantity(value: number | null | undefined): string {
  if (value === null || value === undefined) return '—';
  return Number(value).toLocaleString('en-US', { maximumFractionDigits: 6 });
}

export function percent(value: number | null | undefined): string {
  if (value === null || value === undefined) return '—';
  const sign = value > 0 ? '+' : value < 0 ? '−' : '';
  return `${sign}${Math.abs(value).toFixed(2)}%`;
}

export function trendClass(value: number | null | undefined): string {
  if (!value) return 'muted';
  return value > 0 ? 'up' : 'down';
}

export const SEGMENTS: { key: Segment; label: string }[] = [
  { key: 'US', label: 'Equities · US' },
  { key: 'UK', label: 'Equities · UK' },
  { key: 'IN', label: 'Equities · India' },
  { key: 'FX', label: 'FX' },
  { key: 'CRYPTO', label: 'Crypto' },
];

export function segmentLabel(segment: Segment | string | null | undefined): string {
  return SEGMENTS.find((s) => s.key === segment)?.label ?? (segment || '—');
}

/** Units a client trades in, in plain words. */
export function unitLabel(assetClass: string | null | undefined, symbol = ''): string {
  if (assetClass === 'FX') return `Units of ${symbol.replace('FX:', '').slice(0, 3) || 'currency'}`;
  if (assetClass === 'Crypto') return 'Coins';
  return 'Shares';
}

/** Symbols as people say them: BTC rather than X:BTC-USD, EUR/USD rather than FX:EURUSD. */
export function displaySymbol(symbol: string | null | undefined): string {
  if (!symbol) return '';
  if (symbol.startsWith('X:')) return symbol.slice(2).split('-')[0];
  if (symbol.startsWith('FX:')) return `${symbol.slice(3, 6)}/${symbol.slice(6)}`;
  return symbol;
}

export interface StatusView {
  label: string;
  cls: string;
  open: boolean;
}

/** The status a client sees: plain words, and "Waiting for market" rather than a bare ACCEPTED. */
export function orderStatus(order: Pick<Order, 'status' | 'awaitingMarket'>): StatusView {
  switch (order.status) {
    case 'SUBMITTED':
      return { label: 'Submitted', cls: '', open: true };
    case 'ACCEPTED':
      return order.awaitingMarket
        ? { label: 'Waiting for market', cls: 'waiting', open: true }
        : { label: 'Accepted', cls: 'accepted', open: true };
    case 'FILLED':
      return { label: 'Filled', cls: 'filled', open: false };
    case 'REJECTED':
      return { label: 'Rejected', cls: 'rejected', open: false };
    default:
      return { label: 'Cancelled', cls: 'cancelled', open: false };
  }
}

export function isOpen(order: Pick<Order, 'status'>): boolean {
  return order.status === 'SUBMITTED' || order.status === 'ACCEPTED';
}

/** One audit record as a sentence a dealer or client can read, without knowing the schema. */
export function describeAuditEvent(event: AuditEvent): { title: string; detail: string } {
  const after = event.stateAfter ?? {};
  const before = event.stateBefore ?? {};
  switch (event.entityName) {
    case 'orders': {
      if (after['stage']) {
        const stage = after['stage'] === 'execution' ? 'Priced for execution' : 'Priced at acceptance';
        const ccy = after['instrumentCurrency'];
        const parts = [
          `bid ${price(after['bid'], ccy)} / ask ${price(after['ask'], ccy)}`,
          `quote time ${new Date(after['capturedAt']).toLocaleString()}`,
        ];
        if (after['fxSymbol']) parts.push(`${displaySymbol(after['fxSymbol'])} at ${after['fxRate']}`);
        parts.push(`total ${money(after['settlementAmount'], after['settlementCurrency'])}`);
        if (after['current'] === false) parts.push('market closed: last available price');
        if (after['providerStale']) parts.push('provider flagged price as delayed');
        return { title: stage, detail: parts.join(' · ') };
      }
      if (after['awaiting']) {
        return { title: 'Waiting for a current market price', detail: after['reason'] || '' };
      }
      if (after['request']) {
        const r = after['request'];
        return {
          title: 'Order submitted',
          detail: `${r.side} ${quantity(r.quantity)} ${displaySymbol(r.symbol)} · ${r.orderType}${r.limitPrice ? ' limit ' + r.limitPrice : ''}`,
        };
      }
      if (after['status']) {
        const from = before['status'] ? `${before['status']} → ` : '';
        return { title: `Status ${from}${after['status']}`, detail: after['reason'] || '' };
      }
      return { title: 'Order updated', detail: '' };
    }
    case 'client_accounts':
      return { title: 'Cash balance updated', detail: `${before['cash']} → ${after['cash']}` };
    case 'account_holdings':
      return { title: 'Holding updated', detail: `${quantity(before['quantity'])} → ${quantity(after['quantity'])} units` };
    case 'fills':
      return {
        title: 'Fill recorded',
        detail: `${quantity(after['executedQuantity'])} @ ${price(after['executedPrice'], after['instrumentCurrency'])}`
          + (after['settlementAmount'] ? ` · settled ${money(after['settlementAmount'], after['settlementCurrency'])}` : ''),
      };
    default:
      return { title: `${event.entityName} ${event.actionType.toLowerCase()}`, detail: '' };
  }
}

/** An SVG path for a line chart of values, scaled into a width × height box. */
export function sparkPath(values: number[], width: number, height: number, padding = 8): { line: string; area: string } {
  if (values.length < 2) return { line: '', area: '' };
  const min = Math.min(...values);
  const max = Math.max(...values);
  const span = max - min || 1;
  const step = width / (values.length - 1);
  const points = values.map((v, i) => {
    const x = i * step;
    const y = padding + (height - padding * 2) * (1 - (v - min) / span);
    return `${x.toFixed(1)},${y.toFixed(1)}`;
  });
  const line = 'M' + points.join(' L');
  return { line, area: `${line} L${width},${height} L0,${height} Z` };
}

/** A fresh idempotency key for one order attempt. */
export function newIdempotencyKey(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) return crypto.randomUUID();
  return `${Date.now()}-${Math.random().toString(36).slice(2)}`;
}
