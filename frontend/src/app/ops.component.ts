import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe, JsonPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { OpsApi, OpsOrderSummary, OrderTrace, errorMessage } from './api.service';
import { NavHeaderComponent } from './nav-header.component';
import { describeAuditEvent, displaySymbol, money, orderStatus, price, quantity } from './format';

@Component({
  selector: 'app-ops',
  imports: [DatePipe, JsonPipe, FormsModule, NavHeaderComponent],
  template: `
    <app-nav-header [internal]="true" />
    <main class="page">
      <h1 class="page-title">Trade lookup</h1>
      <p class="page-subtitle">Find any client's order and see exactly what happened and when: pricing, fill, cash and holdings. Every lookup is recorded.</p>

      <form class="card search" (ngSubmit)="search()" role="search">
        <div class="field grow">
          <label for="q">Order number, client email or symbol</label>
          <input id="q" name="q" class="input" [(ngModel)]="query" placeholder="e.g. 1042, joanna@example.com or AAPL" />
        </div>
        <div class="field">
          <label for="status">Status</label>
          <select id="status" name="status" class="input" [(ngModel)]="status">
            <option value="">Any</option>
            <option value="ACCEPTED">Accepted / waiting</option>
            <option value="FILLED">Filled</option>
            <option value="REJECTED">Rejected</option>
          </select>
        </div>
        <button type="submit" class="btn btn-primary" [disabled]="searching()">{{ searching() ? 'Searching…' : 'Search' }}</button>
      </form>

      @if (error()) { <p class="alert error" role="alert">{{ error() }}</p> }

      <div class="layout">
        <section class="card flush results">
          <div class="card-head"><h2>Orders</h2><span class="muted small">{{ results().length }} shown</span></div>
          @if (results().length === 0) {
            <p class="empty">{{ searched() ? 'No orders match.' : 'Search to find orders.' }}</p>
          } @else {
            <div class="table-wrap">
              <table class="data">
                <thead><tr><th>#</th><th>Client</th><th>Order</th><th>Status</th></tr></thead>
                <tbody>
                  @for (r of results(); track r.order.orderId) {
                    <tr class="clickable" [class.selected]="trace()?.summary?.order?.orderId === r.order.orderId" (click)="open(r)">
                      <td class="num"><button type="button" class="link" (click)="$event.stopPropagation(); open(r)">{{ r.order.orderId }}</button></td>
                      <td><div class="instrument-cell"><span>{{ r.clientName }}</span><span class="sub">{{ r.clientEmail }}</span></div></td>
                      <td><div class="instrument-cell"><span class="bold">{{ r.order.side === 'BUY' ? 'Buy' : 'Sell' }} {{ fmt.quantity(r.order.quantity) }} {{ fmt.displaySymbol(r.order.symbol) }}</span><span class="sub">{{ r.order.submittedAt | date: 'd MMM y, HH:mm' }}</span></div></td>
                      <td><span class="badge {{ fmt.orderStatus(r.order).cls }}">{{ fmt.orderStatus(r.order).label }}</span></td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
        </section>

        <section class="card trace" aria-live="polite">
          @if (loadingTrace()) {
            <p class="skeleton">Loading trade record…</p>
          } @else if (trace(); as t) {
            <div class="trace-head">
              <div>
                <p class="eyebrow">Order #{{ t.summary.order.orderId }}</p>
                <h2>{{ t.summary.order.side === 'BUY' ? 'Buy' : 'Sell' }} {{ fmt.quantity(t.summary.order.quantity) }} {{ fmt.displaySymbol(t.summary.order.symbol) }}</h2>
                <p class="hint">{{ t.summary.clientName }} · {{ t.summary.clientEmail }} · client {{ t.summary.clientId }}</p>
              </div>
              <span class="badge {{ fmt.orderStatus(t.summary.order).cls }}">{{ fmt.orderStatus(t.summary.order).label }}</span>
            </div>

            <dl class="facts">
              <div><dt>Placed</dt><dd>{{ t.summary.order.submittedAt | date: 'd MMM y, HH:mm:ss' }}</dd></div>
              <div><dt>Type</dt><dd>{{ t.summary.order.orderType === 'LIMIT' ? 'Limit ' + fmt.price(t.summary.order.limitPrice, t.summary.order.instrumentCurrency) : 'Market' }}</dd></div>
              <div><dt>Fill price</dt><dd class="num">{{ fmt.price(t.summary.order.executedPrice, t.summary.order.instrumentCurrency) }}</dd></div>
              <div><dt>Settled</dt><dd class="num">{{ fmt.money(t.summary.order.settlementAmount, t.summary.order.settlementCurrency) }}</dd></div>
              @if (t.summary.order.rejectionReason) { <div class="wide"><dt>Rejection reason</dt><dd>{{ t.summary.order.rejectionReason }}</dd></div> }
            </dl>

            <h3>Timeline</h3>
            <ol class="timeline">
              @for (e of events(); track e.id) {
                <li>
                  <span class="when">{{ e.at | date: 'd MMM y, HH:mm:ss.SSS' }}</span>
                  <span class="what">{{ e.title }}</span>
                  @if (e.detail) { <span class="why">{{ e.detail }}</span> }
                  <details><summary>Recorded data</summary><pre>{{ e.raw | json }}</pre></details>
                </li>
              }
            </ol>

            @if (t.ledger.length) {
              <h3>Ledger</h3>
              <div class="table-wrap">
                <table class="data">
                  <thead><tr><th>Entry</th><th>Type</th><th class="right">Qty</th><th class="right">Unit</th><th class="right">Amount</th></tr></thead>
                  <tbody>
                    @for (l of t.ledger; track l.transactionId) {
                      <tr><td class="num">{{ l.transactionId }}</td><td>{{ l.type }}</td><td class="num right">{{ fmt.quantity(l.quantity) }}</td><td class="num right">{{ fmt.money(l.unitPrice, t.summary.order.settlementCurrency) }}</td><td class="num right bold">{{ fmt.money(l.netAmount, t.summary.order.settlementCurrency) }}</td></tr>
                    }
                  </tbody>
                </table>
              </div>
            }
          } @else {
            <p class="empty">Select an order to see its full record.</p>
          }
        </section>
      </div>
    </main>
  `,
  styles: [`
    .search { display: flex; gap: 16px; align-items: flex-end; flex-wrap: wrap; }
    .search .field { margin: 0; min-width: 180px; }
    .search .grow { flex: 1 1 320px; }
    .layout { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(460px, 100%), 1fr)); gap: 24px; align-items: start; }
    .small { font-size: 13px; }
    tr.selected td { background: var(--brand-soft); }
    .trace-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; margin-bottom: 16px; }
    .eyebrow { font-size: 12px; font-weight: 600; color: var(--text-muted); text-transform: uppercase; letter-spacing: .04em; margin: 0 0 4px; }
    .trace h2 { font-size: 20px; }
    h3 { font-size: 14px; text-transform: uppercase; letter-spacing: .04em; color: var(--text-muted); margin: 24px 0 12px; }
    .facts { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(160px, 100%), 1fr)); gap: 14px; margin: 0; padding: 16px 0; border-top: 1px solid var(--border); border-bottom: 1px solid var(--border); }
    .facts dt { font-size: 12px; color: var(--text-muted); margin-bottom: 4px; }
    .facts dd { margin: 0; font-weight: 600; font-size: 14px; }
    .facts .wide { grid-column: 1 / -1; }
    .timeline { list-style: none; margin: 0; padding: 0; border-left: 2px solid var(--border); }
    .timeline li { position: relative; padding: 0 0 14px 16px; font-size: 13px; display: flex; flex-direction: column; gap: 2px; }
    .timeline li::before { content: ''; position: absolute; left: -6px; top: 4px; width: 10px; height: 10px; border-radius: 999px; background: var(--brand); }
    .when { color: var(--text-muted); font-size: 12px; font-variant-numeric: tabular-nums; }
    .what { font-weight: 600; }
    .why { color: var(--text-muted); }
    details summary { cursor: pointer; color: var(--text-muted); font-size: 12px; margin-top: 2px; }
    pre { background: var(--bg); border-radius: 8px; padding: 10px; font-size: 11px; overflow-x: auto; margin: 6px 0 0; }
  `],
})
export class OpsComponent implements OnInit {
  private readonly ops = inject(OpsApi);

  protected readonly fmt = { money, price, quantity, displaySymbol, orderStatus };
  protected query = '';
  protected status = '';
  protected readonly results = signal<OpsOrderSummary[]>([]);
  protected readonly searching = signal(false);
  protected readonly searched = signal(false);
  protected readonly trace = signal<OrderTrace | null>(null);
  protected readonly loadingTrace = signal(false);
  protected readonly error = signal('');

  protected readonly events = computed(() =>
    (this.trace()?.timeline ?? []).map((e) => ({
      id: e.auditId,
      at: e.recordedAt,
      raw: { before: e.stateBefore, after: e.stateAfter },
      ...describeAuditEvent(e),
    })));

  ngOnInit(): void {
    this.search();
  }

  search(): void {
    this.searching.set(true);
    this.error.set('');
    this.ops.search(this.query, this.status).subscribe({
      next: (rows) => {
        this.results.set(rows);
        this.searching.set(false);
        this.searched.set(true);
        // An exact order number opens straight away
        if (/^\d+$/.test(this.query.trim()) && rows.length === 1) this.open(rows[0]);
      },
      error: (e) => {
        this.searching.set(false);
        this.error.set(errorMessage(e, 'Search failed.'));
      },
    });
  }

  open(row: OpsOrderSummary): void {
    this.loadingTrace.set(true);
    this.ops.trace(row.order.orderId).subscribe({
      next: (t) => {
        this.trace.set(t);
        this.loadingTrace.set(false);
      },
      error: (e) => {
        this.loadingTrace.set(false);
        this.error.set(errorMessage(e, 'That trade record could not be loaded.'));
      },
    });
  }
}
