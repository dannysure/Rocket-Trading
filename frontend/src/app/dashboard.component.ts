import { ChangeDetectorRef, Component, inject, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { PortfolioService, OrderService, QuoteService, PortfolioSummaryResponse, OrderResponse, QuoteResponse, Position } from './api.service';
import { AuthService } from './api.service';
import { OAuthService } from './oauth.service';
import { Subscription, finalize, switchMap, take, takeWhile, timer } from 'rxjs';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="dashboard">
      <header class="header">
        <h1>Rocket Trading Dashboard</h1>
        <div class="user-info">
          <span>{{ session?.email }}</span>
          <button (click)="onSignOut()" class="button button-small">Sign Out</button>
        </div>
      </header>

      <div class="content">
        <div class="section">
          <h2>Portfolio Summary</h2>
          <div *ngIf="loading.portfolio" class="loading">Loading portfolio...</div>
          <div *ngIf="!loading.portfolio && portfolio" class="portfolio-card">
            <div class="portfolio-stat">
              <span class="label">Cash Balance</span>
              <span class="value">{{ portfolio.currency }} {{ portfolio.cashBalance | number:'1.2-2' }}</span>
            </div>
            <div class="portfolio-stat">
              <span class="label">Positions</span>
              <span class="value">{{ portfolio.positions.length }}</span>
            </div>
          </div>

          <h3>Holdings</h3>
          <div *ngIf="!loading.portfolio && portfolio && portfolio.positions.length > 0" class="positions">
            <table class="table">
              <thead>
                <tr>
                  <th>Symbol</th>
                  <th>Quantity</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                <tr *ngFor="let position of portfolio.positions">
                  <td>{{ position.symbol }}</td>
                  <td>{{ position.quantity | number:'1.6-6' }}</td>
                  <td><button type="button" class="button button-small" (click)="onSellPosition(position)">Sell</button></td>
                </tr>
              </tbody>
            </table>
          </div>
          <div *ngIf="!loading.portfolio && portfolio && portfolio.positions.length === 0">
            <p class="info">No positions yet. Place an order to get started!</p>
          </div>
        </div>

        <div class="section">
          <h2>Place Order</h2>
          <form (ngSubmit)="onPlaceOrder()" class="order-form">
            <div class="form-row">
              <div class="form-group">
                <label for="symbol">Symbol</label>
                <input
                  id="symbol"
                  type="text"
                  [(ngModel)]="orderForm.symbol"
                  name="symbol"
                  class="input"
                  (blur)="getQuote()"
                  placeholder="e.g., AAPL, SPY, X:BTC-USD"
                />
              </div>

              <div class="form-group">
                <label for="side">Side</label>
                <select [(ngModel)]="orderForm.side" name="side" class="input">
                  <option value="BUY">BUY</option>
                  <option value="SELL">SELL</option>
                </select>
              </div>

              <div class="form-group">
                <label for="quantity">Quantity</label>
                <input
                  id="quantity"
                  type="number"
                  [(ngModel)]="orderForm.quantity"
                  name="quantity"
                  class="input"
                  step="0.01"
                />
              </div>
            </div>

            <div *ngIf="currentQuote" class="quote-info">
              <p>
                <strong>{{ orderForm.symbol }}</strong> - Bid: {{ currentQuote.bid }}, Ask: {{ currentQuote.ask }}
                <span class="quote-time">as of {{ currentQuote.capturedAt | date:'shortTime' }}</span>
                <span *ngIf="currentQuote.delayed" class="delayed-badge">Delayed price</span>
              </p>
              <p>
                Estimated {{ orderForm.side === 'BUY' ? 'cost' : 'proceeds' }}:
                {{ (orderForm.side === 'BUY' ? currentQuote.ask : currentQuote.bid) * orderForm.quantity | number:'1.2-2' }}
              </p>
            </div>

            <button type="submit" class="button button-primary" [disabled]="loading.order">
              {{ loading.order ? 'Placing Order...' : 'Place Order' }}
            </button>
          </form>

          <div *ngIf="orderError" class="error-message">
            {{ orderError }}
          </div>
          <div *ngIf="orderSuccess" class="success-message">
            Order placed successfully!
          </div>
        </div>

        <div class="section">
          <h2>Recent Orders</h2>
          <div *ngIf="loading.orders" class="loading">Loading orders...</div>
          <div *ngIf="!loading.orders && orders.length > 0" class="orders">
            <table class="table">
              <thead>
                <tr>
                  <th>Order ID</th>
                  <th>Symbol</th>
                  <th>Side</th>
                  <th>Quantity</th>
                  <th>Status</th>
                  <th>Reason</th>
                  <th>Submitted</th>
                </tr>
              </thead>
              <tbody>
                <tr *ngFor="let order of orders">
                  <td>{{ order.orderId }}</td>
                  <td>{{ order.symbol }}</td>
                  <td>{{ order.side }}</td>
                  <td>{{ order.quantity | number:'1.6-6' }}</td>
                  <td><span class="status" [ngClass]="order.status.toLowerCase()">{{ order.status }}</span></td>
                  <td>{{ order.rejectionReason }}</td>
                  <td>{{ order.submittedAt | date:'short' }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <div *ngIf="!loading.orders && orders.length === 0">
            <p class="info">No orders yet.</p>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .dashboard {
      min-height: 100vh;
      background: #f5f5f5;
    }

    .header {
      background: #333;
      color: white;
      padding: 1.5rem;
      display: flex;
      justify-content: space-between;
      align-items: center;
    }

    .user-info {
      display: flex;
      gap: 1rem;
      align-items: center;
    }

    .button-small {
      padding: 0.5rem 1rem;
      background: #667eea;
      color: white;
      border: none;
      border-radius: 4px;
      cursor: pointer;
    }

    .button-small:hover {
      background: #5568d3;
    }

    .content {
      padding: 2rem;
      max-width: 1200px;
      margin: 0 auto;
    }

    .section {
      background: white;
      padding: 1.5rem;
      border-radius: 8px;
      margin-bottom: 2rem;
      box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
    }

    h2 {
      margin-top: 0;
      color: #333;
      border-bottom: 2px solid #667eea;
      padding-bottom: 0.5rem;
    }

    h3 {
      color: #555;
      margin-top: 1.5rem;
    }

    .portfolio-card {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
      gap: 1rem;
      margin-bottom: 1.5rem;
    }

    .portfolio-stat {
      padding: 1rem;
      background: #f9f9f9;
      border-radius: 4px;
      border-left: 4px solid #667eea;
    }

    .label {
      display: block;
      font-size: 0.9rem;
      color: #999;
      margin-bottom: 0.5rem;
    }

    .value {
      display: block;
      font-size: 1.3rem;
      font-weight: bold;
      color: #333;
    }

    .quote-time {
      color: #666;
      font-size: 0.85rem;
      margin-left: 0.5rem;
    }

    .delayed-badge {
      background: #fff3cd;
      color: #856404;
      border-radius: 4px;
      font-size: 0.75rem;
      font-weight: 600;
      margin-left: 0.5rem;
      padding: 0.15rem 0.5rem;
    }

    .table {
      width: 100%;
      border-collapse: collapse;
      margin-top: 1rem;
    }

    thead {
      background: #f5f5f5;
    }

    th {
      padding: 0.75rem;
      text-align: left;
      font-weight: 600;
      color: #333;
    }

    td {
      padding: 0.75rem;
      border-bottom: 1px solid #eee;
      color: #333;
    }

    tr:hover {
      background: #f9f9f9;
    }

    .status {
      padding: 0.25rem 0.75rem;
      border-radius: 12px;
      font-size: 0.85rem;
      font-weight: 600;
    }

    .status.submitted {
      background: #fff3cd;
      color: #856404;
    }

    .status.accepted {
      background: #d1ecf1;
      color: #0c5460;
    }

    .status.filled {
      background: #d4edda;
      color: #155724;
    }

    .status.rejected {
      background: #f8d7da;
      color: #721c24;
    }

    .order-form {
      background: #f9f9f9;
      padding: 1rem;
      border-radius: 4px;
      margin: 1rem 0;
    }

    .form-row {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
      gap: 1rem;
      margin-bottom: 1rem;
    }

    .form-group {
      display: flex;
      flex-direction: column;
    }

    label {
      font-weight: 500;
      margin-bottom: 0.5rem;
      color: #333;
    }

    .input {
      padding: 0.75rem;
      border: 1px solid #ddd;
      border-radius: 4px;
      font-size: 1rem;
    }

    .input:focus {
      outline: none;
      border-color: #667eea;
      box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
    }

    .quote-info {
      background: #e8f4f8;
      padding: 0.75rem;
      border-radius: 4px;
      margin-bottom: 1rem;
      color: #0c5460;
    }

    .button {
      padding: 0.75rem 1.5rem;
      border: none;
      border-radius: 4px;
      cursor: pointer;
      font-weight: 500;
    }

    .button-primary {
      background: #667eea;
      color: white;
    }

    .button-primary:hover {
      background: #5568d3;
    }

    .button-primary:disabled {
      background: #ccc;
      cursor: not-allowed;
    }

    .error-message {
      background: #fee;
      color: #c33;
      padding: 0.75rem;
      border-radius: 4px;
      margin-top: 1rem;
    }

    .success-message {
      background: #efe;
      color: #3c3;
      padding: 0.75rem;
      border-radius: 4px;
      margin-top: 1rem;
    }

    .loading {
      text-align: center;
      color: #999;
      padding: 2rem;
    }

    .info {
      color: #999;
      text-align: center;
      padding: 1rem;
    }
  `]
})
export class DashboardComponent implements OnInit, OnDestroy {
  private portfolioService = inject(PortfolioService);
  private orderService = inject(OrderService);
  private quoteService = inject(QuoteService);
  private authService = inject(AuthService);
  private oauthService = inject(OAuthService);
  private router = inject(Router);
  // The app is zoneless, so async callbacks must mark the view dirty for state changes to render
  private cdr = inject(ChangeDetectorRef);

  session = this.authService.session();
  portfolio: PortfolioSummaryResponse | null = null;
  orders: OrderResponse[] = [];
  currentQuote: QuoteResponse | null = null;

  loading = {
    portfolio: true,
    orders: true,
    order: false,
  };

  orderError = '';
  orderSuccess = false;

  orderForm = {
    symbol: '',
    side: 'BUY',
    quantity: 1,
  };

  private subscriptions: Subscription[] = [];
  // Reused only when retrying the same order after an uncertain failure, so a retry can't create a duplicate trade
  private pendingOrder: { payload: string; key: string } | null = null;

  ngOnInit(): void {
    if (!this.authService.isSignedIn()) {
      this.router.navigate(['/sign-in']);
      return;
    }

    this.loadPortfolio();
    this.loadOrders();
  }

  ngOnDestroy(): void {
    this.subscriptions.forEach(sub => sub.unsubscribe());
  }

  private loadPortfolio(): void {
    this.loading.portfolio = true;
    this.subscriptions.push(
      this.portfolioService.getPortfolioSummary().subscribe({
        next: (response) => {
          this.portfolio = response.data!;
          this.loading.portfolio = false;
          this.cdr.markForCheck();
        },
        error: (error) => {
          console.error('Failed to load portfolio:', error);
          this.loading.portfolio = false;
          this.cdr.markForCheck();
        },
      })
    );
  }

  private loadOrders(): void {
    this.loading.orders = true;
    this.subscriptions.push(
      this.orderService.listOrders().subscribe({
        next: (response) => {
          this.orders = response.data || [];
          this.loading.orders = false;
          this.cdr.markForCheck();
        },
        error: (error) => {
          console.error('Failed to load orders:', error);
          this.loading.orders = false;
          this.cdr.markForCheck();
        },
      })
    );
  }

  getQuote(): void {
    if (!this.orderForm.symbol) return;

    this.subscriptions.push(
      this.quoteService.getQuote(this.orderForm.symbol).subscribe({
        next: (response) => {
          this.currentQuote = response.data || null;
          this.cdr.markForCheck();
        },
        error: (error) => {
          console.error('Failed to get quote:', error);
          this.currentQuote = null;
          this.cdr.markForCheck();
        },
      })
    );
  }

  onPlaceOrder(): void {
    if (!this.portfolio) {
      this.orderError = 'Your trading account could not be loaded. Please refresh or sign in again.';
      return;
    }
    if (!this.orderForm.symbol || this.orderForm.quantity <= 0) {
      this.orderError = 'Please fill in all fields';
      return;
    }

    this.loading.order = true;
    this.orderError = '';
    this.orderSuccess = false;

    const symbol = this.orderForm.symbol.trim().toUpperCase();
    const request = {
      symbol,
      side: this.orderForm.side as 'BUY' | 'SELL',
      quantity: this.orderForm.quantity,
      // Fauxnance crypto symbols look like X:BTC-USD
      market: symbol.startsWith('X:') ? 'crypto' as const : 'stock' as const,
      orderType: 'MARKET' as const,
    };

    const payload = JSON.stringify(request);
    if (this.pendingOrder?.payload !== payload) {
      this.pendingOrder = { payload, key: crypto.randomUUID() };
    }

    this.subscriptions.push(
      this.orderService.submitOrder(request, this.pendingOrder.key).subscribe({
        next: (response) => {
          this.pendingOrder = null;
          this.loading.order = false;
          this.orderSuccess = true;
          this.orderForm = { symbol: '', side: 'BUY', quantity: 1 };
          this.currentQuote = null;
          this.cdr.markForCheck();
          this.loadOrders();
          if (response.data) {
            this.pollUntilSettled(response.data.orderId);
          }
          setTimeout(() => {
            this.orderSuccess = false;
            this.cdr.markForCheck();
          }, 2000);
        },
        error: (error) => {
          // Network errors and 5xx may have created the order server-side; keep the key so a retry is deduplicated
          if (error.status !== 0 && error.status < 500) {
            this.pendingOrder = null;
          }
          this.loading.order = false;
          this.orderError = error.error?.error?.message || 'Failed to place order';
          this.cdr.markForCheck();
          // Rejected orders are still recorded, so show them in Recent Orders
          this.loadOrders();
        },
      })
    );
  }

  onSellPosition(position: Position): void {
    this.orderForm = { symbol: position.symbol, side: 'SELL', quantity: position.quantity };
    this.orderError = '';
    this.getQuote();
    document.getElementById('symbol')?.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }

  // Orders are accepted first and filled by a background worker, so poll until the order settles
  private pollUntilSettled(orderId: number): void {
    this.subscriptions.push(
      timer(0, 2000).pipe(
        switchMap(() => this.orderService.getOrder(orderId)),
        takeWhile(response => response.data?.status === 'SUBMITTED' || response.data?.status === 'ACCEPTED', true),
        take(30),
      ).subscribe({
        complete: () => {
          this.loadPortfolio();
          this.loadOrders();
        },
        error: (error) => console.error('Failed to poll order status:', error),
      })
    );
  }

  onSignOut(): void {
    // Both services keep their own in-memory copy of the shared stored session, so clear both
    const signOut$ = this.oauthService.isAuthenticated()
      ? this.oauthService.signOut()
      : this.authService.signOut();

    signOut$.pipe(finalize(() => {
      this.authService.clearSession();
      this.router.navigate(['/login']);
    })).subscribe({
      error: (error) => console.error('Sign out error:', error),
    });
  }
}
