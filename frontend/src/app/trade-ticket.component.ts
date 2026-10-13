import { Component, DestroyRef, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { Subject, Subscription, catchError, debounceTime, of, switchMap, take, takeWhile, timer } from 'rxjs';
import { IndicativePrice, Instrument, MarketApi, Order, OrderApi, Side, errorMessage } from './api.service';
import { BrandMarkComponent } from './brand-mark.component';
import { displaySymbol, isOpen, money, newIdempotencyKey, orderStatus, price, quantity, unitLabel } from './format';

type Step = 'ticket' | 'review' | 'placed';

@Component({
  selector: 'app-trade-ticket',
  imports: [FormsModule, RouterLink, BrandMarkComponent],
  template: `
    @switch (step()) {
      @case ('ticket') {
        <div class="sides" role="radiogroup" aria-label="Buy or sell">
          <button type="button" role="radio" class="side-btn buy" [attr.aria-checked]="side() === 'BUY'" [class.active]="side() === 'BUY'" (click)="setSide('BUY')">Buy</button>
          <button type="button" role="radio" class="side-btn sell" [attr.aria-checked]="side() === 'SELL'" [class.active]="side() === 'SELL'" (click)="setSide('SELL')">Sell</button>
        </div>

        <label class="field-label" for="ticket-qty">{{ units() }}</label>
        <input id="ticket-qty" class="input qty" type="number" inputmode="decimal" min="0" step="any"
               [ngModel]="qty()" (ngModelChange)="setQty($event)" />

        <div class="quick" aria-label="Quick amounts">
          @for (pct of quickAmounts; track pct) {
            <button type="button" class="quick-btn" (click)="applyQuick(pct)" [disabled]="!indicative()">{{ pct === 1 ? 'Max' : pct * 100 + '%' }}</button>
          }
        </div>
        @if (side() === 'SELL') {
          <p class="hint own">You own {{ held() }} {{ units().toLowerCase() }}</p>
        }

        <details class="more">
          <summary>Price protection (limit)</summary>
          <label class="field-label" for="ticket-limit">{{ side() === 'BUY' ? 'Pay no more than' : 'Accept no less than' }} ({{ indicative()?.instrumentCurrency ?? '' }})</label>
          <input id="ticket-limit" class="input" type="number" min="0" step="any" placeholder="Leave blank for market price"
                 [ngModel]="limit()" (ngModelChange)="limit.set($event)" />
          <p class="hint">If the price moves past this, the order is rejected instead of filled.</p>
        </details>

        <div class="row"><span class="muted">Market price</span><span class="num bold">{{ unitPriceText() }}</span></div>
        @if (fxLine()) { <div class="row"><span class="muted">Exchange rate</span><span class="num">{{ fxLine() }}</span></div> }
        <div class="row total"><span class="muted">Estimated total</span><span class="num bold">{{ totalText() }}</span></div>

        @if (indicative() && !indicative()!.marketOpen) {
          <p class="alert warn">This market is closed right now. Your order will be accepted now and filled at the first current price when it reopens.</p>
        }
        @if (quoteError()) { <p class="alert error" role="alert">{{ quoteError() }}</p> }
        @if (fundsProblem()) { <p class="alert error" role="alert">{{ fundsProblem() }}</p> }

        <button type="button" class="btn btn-primary btn-block" (click)="step.set('review')" [disabled]="!canReview()">Review order</button>
        <p class="footnote center">Priced at the current quote. The fill price may differ slightly.</p>
      }

      @case ('review') {
        <h3>Review order</h3>
        <dl class="summary">
          <div><dt>Action</dt><dd class="side" [class.buy]="side() === 'BUY'" [class.sell]="side() === 'SELL'">{{ side() === 'BUY' ? 'Buy' : 'Sell' }} {{ symbolText() }}</dd></div>
          <div><dt>{{ units() }}</dt><dd class="num">{{ qtyText() }}</dd></div>
          <div><dt>Indicative price</dt><dd class="num">{{ unitPriceText() }}</dd></div>
          @if (fxLine()) { <div><dt>Exchange rate</dt><dd class="num">{{ fxLine() }}</dd></div> }
          @if (limitValue()) { <div><dt>Price protection</dt><dd class="num">{{ limitText() }}</dd></div> }
          <div class="total"><dt>Estimated total</dt><dd class="num">{{ totalText() }}</dd></div>
        </dl>
        @if (submitError()) { <p class="alert error" role="alert">{{ submitError() }}</p> }
        <button type="button" class="btn btn-primary btn-block" (click)="place()" [disabled]="placing()">{{ placing() ? 'Placing order…' : 'Confirm & place order' }}</button>
        <button type="button" class="btn btn-ghost btn-block" (click)="step.set('ticket')" [disabled]="placing()">Edit order</button>
      }

      @case ('placed') {
        <div class="placed" aria-live="polite">
          <app-brand-mark [size]="48" color="#1F49E6" />
          <h3>{{ placedTitle() }}</h3>
          <p class="hint">{{ side() === 'BUY' ? 'Buy' : 'Sell' }} {{ qtyText() }} {{ symbolText() }}</p>
          @if (placedOrder(); as order) {
            <span class="badge {{ statusView().cls }}">{{ statusView().label }}</span>
            <p class="hint explain">{{ statusExplanation() }}</p>
          }
        </div>
        <a routerLink="/blotter" class="btn btn-primary btn-block">View in blotter</a>
        <button type="button" class="btn btn-ghost btn-block" (click)="reset()">Place another order</button>
      }
    }
  `,
  styles: [`
    :host { display: block; }
    .sides { display: flex; gap: 8px; margin-bottom: 20px; }
    .side-btn { flex: 1; padding: 10px 0; min-height: 44px; border-radius: 8px; font-weight: 700; font-size: 14px; border: none; cursor: pointer; background: var(--surface-alt); color: var(--text-muted); }
    .side-btn.buy.active { background: var(--green-soft); color: var(--green); }
    .side-btn.sell.active { background: var(--red-soft); color: var(--red); }
    .field-label { display: block; font-size: 13px; font-weight: 600; color: var(--text-muted); margin-bottom: 6px; }
    .qty { font-size: 16px; }
    .quick { display: flex; gap: 6px; margin: 10px 0 4px; }
    .quick-btn { flex: 1; padding: 6px 0; min-height: 32px; border-radius: 999px; font-size: 12px; font-weight: 600; border: 1px solid var(--border); background: transparent; color: var(--text-muted); cursor: pointer; }
    .quick-btn:hover:not(:disabled) { color: var(--text); border-color: var(--text-muted); }
    .own { margin: 6px 0 0; }
    .more { margin: 14px 0 8px; font-size: 13px; }
    .more summary { cursor: pointer; color: var(--text-muted); font-weight: 600; padding: 6px 0; }
    .more .input { margin-bottom: 0; }
    .row { display: flex; justify-content: space-between; gap: 12px; font-size: 14px; padding: 10px 0; border-top: 1px solid var(--border); }
    .row.total { margin-bottom: 16px; }
    .footnote.center { text-align: center; margin: 12px 0 0; }
    h3 { font-size: 18px; margin-bottom: 16px; }
    .summary { margin: 0 0 20px; display: flex; flex-direction: column; gap: 12px; font-size: 14px; }
    .summary div { display: flex; justify-content: space-between; gap: 12px; }
    .summary dt { color: var(--text-muted); }
    .summary dd { margin: 0; font-weight: 600; text-align: right; }
    .summary .total { border-top: 1px solid var(--border); padding-top: 12px; }
    .summary .total dd { font-weight: 700; }
    .placed { text-align: center; padding: 8px 0 16px; }
    .placed h3 { margin: 14px 0 4px; }
    .placed .hint { margin-bottom: 14px; }
    .explain { margin-top: 14px; }
    .btn-block + .btn-block { margin-top: 8px; }
  `],
})
export class TradeTicketComponent {
  readonly instrument = input.required<Instrument>();
  /** Emitted when an order reaches a final state, so the page can refresh balances. */
  readonly settled = output<Order>();

  private readonly market = inject(MarketApi);
  private readonly orders = inject(OrderApi);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly quickAmounts = [0.25, 0.5, 0.75, 1];
  protected readonly step = signal<Step>('ticket');
  protected readonly side = signal<Side>('BUY');
  protected readonly qty = signal<number | null>(1);
  protected readonly limit = signal<number | null>(null);
  protected readonly indicative = signal<IndicativePrice | null>(null);
  protected readonly quoteError = signal('');
  protected readonly placing = signal(false);
  protected readonly submitError = signal('');
  protected readonly placedOrder = signal<Order | null>(null);

  private readonly priceRequests = new Subject<{ symbol: string; side: Side; qty: number }>();
  private pendingKey: { fingerprint: string; key: string } | null = null;
  private polling?: Subscription;

  protected readonly units = computed(() => unitLabel(this.instrument().assetClass, this.instrument().symbol));
  protected readonly symbolText = computed(() => displaySymbol(this.instrument().symbol));
  protected readonly limitValue = computed(() => {
    const v = Number(this.limit());
    return this.limit() !== null && this.limit() !== undefined && String(this.limit()) !== '' && v > 0 ? v : null;
  });
  protected readonly held = computed(() => quantity(this.indicative()?.heldQuantity ?? 0));
  protected readonly qtyText = computed(() => quantity(this.qty()));
  protected readonly unitPriceText = computed(() => {
    const p = this.indicative();
    return p ? price(p.unitPrice, p.instrumentCurrency) : '—';
  });
  protected readonly totalText = computed(() => {
    const p = this.indicative();
    return p && this.validQty() ? money(p.estimatedTotal, p.settlementCurrency) : '—';
  });
  protected readonly limitText = computed(() => price(this.limitValue(), this.indicative()?.instrumentCurrency));
  protected readonly fxLine = computed(() => {
    const p = this.indicative();
    if (!p || p.instrumentCurrency === p.settlementCurrency) return '';
    return `1 ${p.instrumentCurrency} = ${Number(p.fxRate).toPrecision(4)} ${p.settlementCurrency}`;
  });
  protected readonly fundsProblem = computed(() => {
    const p = this.indicative();
    if (!p || !this.validQty()) return '';
    if (p.side === 'BUY' && p.estimatedTotal > p.availableCash) {
      return `That costs more than your available cash (${money(p.availableCash, p.settlementCurrency)}).`;
    }
    if (p.side === 'SELL' && Number(this.qty()) > p.heldQuantity) {
      return `You only own ${quantity(p.heldQuantity)}.`;
    }
    return '';
  });
  protected readonly canReview = computed(() =>
    !!this.indicative() && this.validQty() && !this.fundsProblem() && this.indicative()!.quantity === Number(this.qty()));
  protected readonly statusView = computed(() => orderStatus(this.placedOrder() ?? { status: 'SUBMITTED', awaitingMarket: false }));
  protected readonly placedTitle = computed(() => {
    const order = this.placedOrder();
    if (order?.status === 'FILLED') return 'Order filled';
    if (order?.status === 'REJECTED') return 'Order not filled';
    return 'Order placed';
  });
  protected readonly statusExplanation = computed(() => {
    const order = this.placedOrder();
    if (!order) return '';
    switch (order.status) {
      case 'FILLED':
        return `Filled at ${price(order.executedPrice, order.instrumentCurrency)}. Total ${money(order.settlementAmount, order.settlementCurrency)}. Your holdings and cash are updated.`;
      case 'REJECTED':
        return order.rejectionReason ? `Reason: ${order.rejectionReason}. No money has moved.` : 'No money has moved.';
      case 'ACCEPTED':
        return order.awaitingMarket
          ? 'The market is closed, so your order is waiting. It fills at the first current price. Track it on your blotter.'
          : 'Accepted and being filled at the current price…';
      default:
        return 'Checking your order…';
    }
  });

  constructor() {
    this.priceRequests.pipe(
      debounceTime(250),
      switchMap((r) => this.market.indicative(r.symbol, r.side, r.qty).pipe(
        catchError((e) => {
          this.quoteError.set(errorMessage(e, 'No price is available for this instrument right now.'));
          return of(null);
        }),
      )),
      takeUntilDestroyed(),
    ).subscribe((p) => {
      if (p) this.quoteError.set('');
      this.indicative.set(p);
    });

    // Re-price whenever the instrument, side or amount changes
    effect(() => {
      const symbol = this.instrument().symbol;
      const side = this.side();
      const qty = Number(this.qty());
      untracked(() => {
        if (qty > 0) this.priceRequests.next({ symbol, side, qty });
      });
    });

    // A different instrument starts a fresh ticket
    effect(() => {
      this.instrument();
      untracked(() => this.reset());
    });

    this.destroyRef.onDestroy(() => this.polling?.unsubscribe());
  }

  setSide(side: Side): void {
    this.side.set(side);
  }

  setQty(value: number | string | null): void {
    this.qty.set(value === null || value === '' ? null : Number(value));
  }

  applyQuick(pct: number): void {
    const p = this.indicative();
    if (!p) return;
    const wholeUnits = this.instrument().assetClass === 'Equity';
    let amount: number;
    if (this.side() === 'BUY') {
      const perUnit = p.estimatedTotal / p.quantity;
      // Leave a small margin so a price move between quote and fill does not exceed the cash
      amount = perUnit > 0 ? (p.availableCash * pct * 0.995) / perUnit : 0;
    } else {
      amount = p.heldQuantity * pct;
    }
    const factor = wholeUnits && amount >= 1 ? 1 : 10000;
    this.setQty(Math.floor(amount * factor) / factor);
  }

  place(): void {
    const qty = Number(this.qty());
    const limit = this.limitValue();
    const request = {
      symbol: this.instrument().symbol,
      side: this.side(),
      quantity: qty,
      orderType: limit ? ('LIMIT' as const) : ('MARKET' as const),
      ...(limit ? { limitPrice: limit } : {}),
    };
    // The same details reuse the same key, so a retry after a network blip never places a second order
    const fingerprint = JSON.stringify(request);
    if (this.pendingKey?.fingerprint !== fingerprint) {
      this.pendingKey = { fingerprint, key: newIdempotencyKey() };
    }
    this.placing.set(true);
    this.submitError.set('');
    this.orders.submit(request, this.pendingKey.key).subscribe({
      next: (order) => {
        this.pendingKey = null;
        this.placing.set(false);
        this.placedOrder.set(order);
        this.step.set('placed');
        this.watch(order);
      },
      error: (e: HttpErrorResponse) => {
        this.placing.set(false);
        // A definite answer from the server ends this attempt; a lost connection keeps the key for a safe retry
        if (e.status >= 400 && e.status < 500) this.pendingKey = null;
        this.submitError.set(e.error?.error?.code === 'ORDER_REJECTED'
          ? `Order rejected: ${e.error.error.message}. No money has moved.`
          : errorMessage(e, 'Your order could not be placed. Please try again.'));
      },
    });
  }

  reset(): void {
    this.polling?.unsubscribe();
    this.step.set('ticket');
    this.placedOrder.set(null);
    this.submitError.set('');
    this.limit.set(null);
    this.pendingKey = null;
  }

  /** Follows the order until it settles (BR-07), without the client refreshing anything. */
  private watch(order: Order): void {
    this.polling?.unsubscribe();
    if (!isOpen(order) || order.awaitingMarket) {
      this.settled.emit(order);
      return;
    }
    this.polling = timer(800, 1200).pipe(
      switchMap(() => this.orders.get(order.orderId).pipe(catchError(() => of(null)))),
      takeWhile((o) => !o || (isOpen(o) && !o.awaitingMarket), true),
      // Stop after ~3 minutes; the blotter keeps tracking anything still open
      take(150),
    ).subscribe((latest) => {
      if (!latest) return;
      this.placedOrder.set(latest);
      if (!isOpen(latest) || latest.awaitingMarket) this.settled.emit(latest);
    });
  }

  private validQty(): boolean {
    const q = Number(this.qty());
    return Number.isFinite(q) && q > 0;
  }
}
