import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { ApiService } from './api.service';
import { OrderResponse, PortfolioSummaryResponse, QuoteResponse, SessionResponse } from './models';

@Component({
  selector: 'app-root',
  imports: [CommonModule, ReactiveFormsModule, DatePipe, DecimalPipe],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent {
  private readonly formBuilder = inject(FormBuilder);
  private readonly apiService = inject(ApiService);

  readonly registerForm = this.formBuilder.nonNullable.group({
    name: ['', [Validators.required]],
    email: ['', [Validators.required, Validators.email]],
    dateOfBirth: ['1990-01-01'],
    riskProfile: ['Balanced'],
    initialCash: [10000]
  });

  readonly signInForm = this.formBuilder.nonNullable.group({
    email: ['', [Validators.required, Validators.email]]
  });

  readonly quoteForm = this.formBuilder.nonNullable.group({
    symbol: ['AAPL', [Validators.required]],
    market: ['stock', [Validators.required]]
  });

  readonly orderForm = this.formBuilder.nonNullable.group({
    symbol: ['AAPL', [Validators.required]],
    side: ['BUY', [Validators.required]],
    quantity: [1, [Validators.required]],
    market: ['stock', [Validators.required]],
    orderType: ['MARKET', [Validators.required]],
    limitPrice: [null as number | null]
  });

  session: SessionResponse | null = this.loadSession();
  quote: QuoteResponse | null = null;
  portfolio: PortfolioSummaryResponse | null = null;
  orders: OrderResponse[] = [];
  infoMessage = 'Register a fixture client, sign in, then try quote and order flows.';
  errorMessage = '';

  async register(): Promise<void> {
    this.resetMessages();
    try {
      await firstValueFrom(this.apiService.register(this.registerForm.getRawValue()));
      this.infoMessage = 'Client registered. You can sign in with the same email now.';
    } catch (error) {
      this.handleError(error);
    }
  }

  async signIn(): Promise<void> {
    this.resetMessages();
    try {
      const response = await firstValueFrom(this.apiService.signIn(this.signInForm.getRawValue()));
      this.session = response.data;
      localStorage.setItem('rocketTradingToken', response.data.accessToken);
      localStorage.setItem('rocketTradingSession', JSON.stringify(response.data));
      this.infoMessage = `Signed in client ${response.data.clientId}. Loading portfolio...`;
      await this.loadPortfolio();
      await this.loadOrders();
    } catch (error) {
      this.handleError(error);
    }
  }

  async signOut(): Promise<void> {
    this.resetMessages();
    try {
      if (this.session) {
        await firstValueFrom(this.apiService.signOut(this.session.accessToken));
      }
    } catch (error) {
      this.handleError(error);
    } finally {
      this.session = null;
      this.quote = null;
      this.portfolio = null;
      this.orders = [];
      localStorage.removeItem('rocketTradingToken');
      localStorage.removeItem('rocketTradingSession');
      this.infoMessage = 'Signed out.';
    }
  }

  async fetchQuote(): Promise<void> {
    this.resetMessages();
    try {
      const { symbol, market } = this.quoteForm.getRawValue();
      const response = await firstValueFrom(this.apiService.getQuote(symbol, market));
      this.quote = response.data;
      this.infoMessage = `Fetched ${response.data.market} quote for ${response.data.symbol}.`;
    } catch (error) {
      this.handleError(error);
    }
  }

  async submitOrder(): Promise<void> {
    this.resetMessages();
    try {
      const response = await firstValueFrom(this.apiService.submitOrder(this.orderForm.getRawValue()));
      this.infoMessage = `Order ${response.data.orderId} ${response.data.status.toLowerCase()}.`;
      await this.loadPortfolio();
      await this.loadOrders();
    } catch (error) {
      this.handleError(error);
      await this.loadOrders();
    }
  }

  async refreshDashboard(): Promise<void> {
    this.resetMessages();
    try {
      await this.loadPortfolio();
      await this.loadOrders();
      this.infoMessage = 'Dashboard refreshed.';
    } catch (error) {
      this.handleError(error);
    }
  }

  private async loadPortfolio(): Promise<void> {
    const response = await firstValueFrom(this.apiService.getPortfolio());
    this.portfolio = response.data;
  }

  private async loadOrders(): Promise<void> {
    const response = await firstValueFrom(this.apiService.listOrders());
    this.orders = response.data;
  }

  private loadSession(): SessionResponse | null {
    const value = localStorage.getItem('rocketTradingSession');
    return value ? (JSON.parse(value) as SessionResponse) : null;
  }

  private resetMessages(): void {
    this.errorMessage = '';
    this.infoMessage = '';
  }

  private handleError(error: unknown): void {
    const httpError = error as { error?: { error?: { message?: string } }; message?: string };
    this.errorMessage = httpError?.error?.error?.message ?? httpError?.message ?? 'Request failed.';
  }
}
