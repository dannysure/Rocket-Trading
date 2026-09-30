import { Component, inject, onInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { PortfolioService, OrderService, QuoteService, WebSocketService, PortfolioSummaryResponse, OrderResponse, QuoteResponse } from './api.service';
import { AuthService } from './api.service';
import { OAuthService } from './oauth.service';
import { Subscription } from 'rxjs';

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
              <span class="label">Account Type</span>
              <span class="value">{{ portfolio.accountType }}</span>
            </div>
            <div class="portfolio-stat">
              <span class="label">Total Value</span>
              <span class="value">{{ portfolio.totalValue | number:'1.2-2' }}</span>
            </div>
          </div>

          <h3>Holdings</h3>
          <div *ngIf="!loading.portfolio && portfolio && portfolio.positions.length > 0" class="positions">
            <table class="table">
              <thead>
                <tr>
                  <th>Symbol</th>
                  <th>Quantity</th>
                  <th>Avg Cost</th>
                  <th>Current Value</th>
                </tr>
              </thead>
              <tbody>
                <tr *ngFor="let position of portfolio.positions">
                  <td>{{ position.symbol }}</td>
                  <td>{{ position.quantity | number:'1.6-6' }}</td>
                  <td>{{ position.averageCost | number:'1.2-2' }}</td>
                  <td>{{ position.currentValue | number:'1.2-2' }}</td>
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
                  placeholder="e.g., AAPL"
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
              <p><strong>{{ orderForm.symbol }}</strong> - Bid: {{ currentQuote.bid }}, Ask: {{ currentQuote.ask }}</p>
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
  private webSocketService = inject(WebSocketService);
  private router = inject(Router);

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

  ngOnInit(): void {
    if (!this.authService.isSignedIn()) {
      this.router.navigate(['/sign-in']);
      return;
    }

    this.loadPortfolio();
    this.loadOrders();
    this.connectWebSocket();
  }

  ngOnDestroy(): void {
    this.webSocketService.disconnect();
    this.subscriptions.forEach(sub => sub.unsubscribe());
  }

  private loadPortfolio(): void {
    this.loading.portfolio = true;
    this.subscriptions.push(
      this.portfolioService.getPortfolioSummary().subscribe({
        next: (response) => {
          this.portfolio = response.data!;
          this.loading.portfolio = false;
        },
        error: (error) => {
          console.error('Failed to load portfolio:', error);
          this.loading.portfolio = false;
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
        },
        error: (error) => {
          console.error('Failed to load orders:', error);
          this.loading.orders = false;
        },
      })
    );
  }

  private connectWebSocket(): void {
    this.subscriptions.push(
      this.webSocketService.connect().subscribe({
        next: () => {
          console.log('WebSocket connected');
        },
        error: (error) => {
          console.warn('WebSocket connection failed:', error);
        },
      })
    );

    this.subscriptions.push(
      this.webSocketService.messages$.subscribe({
        next: (message) => {
          if (message) {
            console.log('WebSocket message:', message);
            this.loadPortfolio();
            this.loadOrders();
          }
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
        },
        error: (error) => {
          console.error('Failed to get quote:', error);
          this.currentQuote = null;
        },
      })
    );
  }

  onPlaceOrder(): void {
    if (!this.portfolio || !this.orderForm.symbol || this.orderForm.quantity <= 0) {
      this.orderError = 'Please fill in all fields';
      return;
    }

    this.loading.order = true;
    this.orderError = '';
    this.orderSuccess = false;

    const request = {
      instrumentId: 1, // This should be looked up from the symbol
      side: this.orderForm.side as 'BUY' | 'SELL',
      quantity: this.orderForm.quantity,
      orderType: 'MARKET' as const,
      accountId: this.portfolio.accountId,
    };

    this.subscriptions.push(
      this.orderService.submitOrder(request).subscribe({
        next: (response) => {
          this.loading.order = false;
          this.orderSuccess = true;
          this.orderForm = { symbol: '', side: 'BUY', quantity: 1 };
          this.currentQuote = null;
          setTimeout(() => {
            this.orderSuccess = false;
            this.loadPortfolio();
            this.loadOrders();
          }, 2000);
        },
        error: (error) => {
          this.loading.order = false;
          this.orderError = error.error?.error || 'Failed to place order';
        },
      })
    );
  }

  onSignOut(): void {
    const oauthService = inject(OAuthService);
    const authService = inject(AuthService);
    const router = inject(Router);

    // Try OAuth sign-out first
    if (oauthService.isAuthenticated()) {
      oauthService.signOut().subscribe({
        next: () => {
          router.navigate(['/login']);
        },
        error: () => {
          // Clear session even if sign-out fails
          router.navigate(['/login']);
        }
      });
    } else if (authService.isSignedIn()) {
      // Fallback to legacy auth sign-out
      authService.signOut().subscribe({
        next: () => {
          router.navigate(['/login']);
        },
        error: (error) => {
          console.error('Sign out error:', error);
          authService.clearSession();
          router.navigate(['/login']);
        },
      });
    } else {
      router.navigate(['/login']);
    }
  }
}
