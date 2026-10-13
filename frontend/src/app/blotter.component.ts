import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { forkJoin, interval } from 'rxjs';
import { AuditEvent, Fill, Order, OrderApi, errorMessage } from './api.service';
import { NavHeaderComponent } from './nav-header.component';
import { describeAuditEvent, displaySymbol, isOpen, money, orderStatus, price, quantity } from './format';

type Filter = 'ALL' | 'OPEN' | 'FILLED' | 'REJECTED';

interface Detail {
  fills: Fill[];
  events: { at: string; title: string; detail: string }[];
}

@Component({
  selector: 'app-blotter',
  imports: [DatePipe, RouterLink, NavHeaderComponent],
  template: `
    <app-nav-header />
    <main class="page">
      <h1 class="page-title">Blotter</h1>
      <p class="page-subtitle">Every order you've placed, from submission to outcome. Statuses update on their own.</p>

      <div class="pill-row filters" role="tablist" aria-label="Filter orders">
        @for (f of filters; track f.key) {
          <button type="button" role="tab" class="pill" [attr.aria-selected]="filter() === f.key" [class.active]="filter() === f.key" (click)="filter.set(f.key)">
            {{ f.label }} <span class="count">{{ counts()[f.key] }}</span>
          </button>
        }
      </div>
      @if (loadError()) { <p class="alert error" role="alert">{{ loadError() }}</p> }

      <section class="card flush">
        @if (loading()) {
          <p class="empty">Loading orders…</p>
        } @else if (visible().length === 0) {
          <p class="empty">No orders here yet. <a routerLink="/trade" class="link">Place a trade</a></p>
        } @else {
          <div class="table-wrap">
            <table class="data">
              <thead><tr><th>Placed</th><th>Instrument</th><th>Side</th><th class="right">Qty</th><th class="right">Price</th><th class="right">Total</th><th>Status</th><th><span class="sr-only">Details</span></th></tr></thead>
              <tbody>
                @for (o of visible(); track o.orderId) {
                  <tr class="clickable" (click)="toggle(o)">
                    <td class="muted nowrap">{{ o.submittedAt | date: 'd MMM, HH:mm' }}</td>
                    <td><div class="instrument-cell"><span class="symbol">{{ fmt.displaySymbol(o.symbol) }}</span><span class="sub">{{ o.instrumentName }}</span></div></td>
                    <td><span class="side" [class.buy]="o.side === 'BUY'" [class.sell]="o.side === 'SELL'">{{ o.side === 'BUY' ? 'Buy' : 'Sell' }}</span></td>
                    <td class="num right">{{ fmt.quantity(o.quantity) }}</td>
                    <td class="num right">{{ fmt.price(o.executedPrice, o.instrumentCurrency) }}</td>
                    <td class="num right bold">{{ fmt.money(o.settlementAmount, o.settlementCurrency) }}</td>
                    <td><span class="badge {{ fmt.orderStatus(o).cls }}">{{ fmt.orderStatus(o).label }}</span></td>
                    <td class="right">
                      <button type="button" class="link" [attr.aria-expanded]="expanded() === o.orderId" (click)="$event.stopPropagation(); toggle(o)">
                        {{ expanded() === o.orderId ? 'Hide' : 'Details' }}<span class="sr-only"> for order {{ o.orderId }}</span>
                      </button>
                    </td>
                  </tr>
                  @if (expanded() === o.orderId) {
                    <tr class="detail-row">
                      <td colspan="8">
                        <div class="detail">
                          <div>
                            <p class="detail-title">Order #{{ o.orderId }}</p>
                            <p class="hint">{{ o.orderType === 'LIMIT' ? 'Limit ' + fmt.price(o.limitPrice, o.instrumentCurrency) : 'Market order' }}</p>
                            @if (o.rejectionReason) { <p class="alert error">Rejected: {{ o.rejectionReason }}. No money moved.</p> }
                            @if (o.awaitingMarket) { <p class="alert warn">The market had no current price, so this order is waiting to fill at the first one.</p> }
                            @for (f of details()[o.orderId]?.fills ?? []; track f.fillId) {
                              <p class="fill">Filled {{ fmt.quantity(f.executedQuantity) }} at {{ fmt.price(f.executedPrice, f.instrumentCurrency) }}
                                @if (f.instrumentCurrency !== f.settlementCurrency) { · converted at {{ f.fxRate }} }
                                · {{ fmt.money(f.settlementAmount, f.settlementCurrency) }}</p>
                            }
                          </div>
                          <div>
                            <p class="detail-title">What happened</p>
                            @if (!details()[o.orderId]) { <p class="hint">Loading…</p> }
                            <ol class="timeline">
                              @for (e of details()[o.orderId]?.events ?? []; track $index) {
                                <li><span class="when">{{ e.at | date: 'd MMM HH:mm:ss' }}</span><span class="what">{{ e.title }}</span>@if (e.detail) {<span class="why">{{ e.detail }}</span>}</li>
                              }
                            </ol>
                          </div>
                        </div>
                      </td>
                    </tr>
                  }
                }
              </tbody>
            </table>
          </div>
        }
      </section>

      <p class="footnote">Every order's full lifecycle (submission, pricing and outcome) is kept permanently for audit and dispute resolution and can't be changed after the fact.</p>
    </main>
  `,
  styles: [`
    .filters { margin-bottom: 20px; }
    .count { opacity: .7; margin-left: 4px; font-variant-numeric: tabular-nums; }
    .nowrap { white-space: nowrap; }
    .sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); }
    .detail-row td { background: var(--bg); padding: 20px 24px; }
    .detail { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(320px, 100%), 1fr)); gap: 24px; }
    .detail-title { font-size: 12px; font-weight: 700; text-transform: uppercase; letter-spacing: .04em; color: var(--text-muted); margin: 0 0 8px; }
    .fill { font-size: 14px; margin: 8px 0 0; }
    .timeline { list-style: none; margin: 0; padding: 0; border-left: 2px solid var(--border); }
    .timeline li { position: relative; padding: 0 0 12px 16px; font-size: 13px; display: flex; flex-direction: column; gap: 2px; }
    .timeline li::before { content: ''; position: absolute; left: -6px; top: 4px; width: 10px; height: 10px; border-radius: 999px; background: var(--brand); }
    .when { color: var(--text-muted); font-size: 12px; font-variant-numeric: tabular-nums; }
    .what { font-weight: 600; }
    .why { color: var(--text-muted); }
  `],
})
export class BlotterComponent implements OnInit {
  private readonly orderApi = inject(OrderApi);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly fmt = { money, price, quantity, displaySymbol, orderStatus };
  protected readonly filters: { key: Filter; label: string }[] = [
    { key: 'ALL', label: 'All' },
    { key: 'OPEN', label: 'Open' },
    { key: 'FILLED', label: 'Filled' },
    { key: 'REJECTED', label: 'Rejected' },
  ];

  protected readonly orders = signal<Order[]>([]);
  protected readonly loading = signal(true);
  protected readonly loadError = signal('');
  protected readonly filter = signal<Filter>('ALL');
  protected readonly expanded = signal<number | null>(null);
  protected readonly details = signal<Record<number, Detail>>({});

  protected readonly counts = computed(() => {
    const all = this.orders();
    return {
      ALL: all.length,
      OPEN: all.filter(isOpen).length,
      FILLED: all.filter((o) => o.status === 'FILLED').length,
      REJECTED: all.filter((o) => o.status === 'REJECTED').length,
    };
  });
  protected readonly visible = computed(() => {
    const all = this.orders();
    switch (this.filter()) {
      case 'OPEN': return all.filter(isOpen);
      case 'FILLED': return all.filter((o) => o.status === 'FILLED');
      case 'REJECTED': return all.filter((o) => o.status === 'REJECTED');
      default: return all;
    }
  });

  ngOnInit(): void {
    this.load();
    // BR-07: open orders are re-checked every few seconds so status changes appear without a refresh
    const poll = interval(3000).subscribe(() => {
      if (this.orders().some(isOpen)) this.load();
    });
    this.destroyRef.onDestroy(() => poll.unsubscribe());
  }

  toggle(order: Order): void {
    if (this.expanded() === order.orderId) {
      this.expanded.set(null);
      return;
    }
    this.expanded.set(order.orderId);
    this.loadDetail(order.orderId);
  }

  private load(): void {
    const before = new Map(this.orders().map((o) => [o.orderId, o.status]));
    this.orderApi.list().subscribe({
      next: (list) => {
        this.orders.set(list);
        this.loading.set(false);
        this.loadError.set('');
        // An expanded order that just changed state gets its trail refreshed
        const open = this.expanded();
        const changed = list.find((o) => o.orderId === open);
        if (open !== null && changed && before.get(open) !== changed.status) this.loadDetail(open);
      },
      error: (e) => {
        this.loading.set(false);
        this.loadError.set(errorMessage(e, 'Your orders could not be loaded.'));
      },
    });
  }

  private loadDetail(orderId: number): void {
    forkJoin({ fills: this.orderApi.fills(orderId), timeline: this.orderApi.timeline(orderId) }).subscribe({
      next: ({ fills, timeline }) => {
        const events = timeline.events.map((e: AuditEvent) => ({ at: e.recordedAt, ...describeAuditEvent(e) }));
        this.details.update((d) => ({ ...d, [orderId]: { fills, events } }));
      },
      error: () => this.details.update((d) => ({ ...d, [orderId]: { fills: [], events: [] } })),
    });
  }
}
