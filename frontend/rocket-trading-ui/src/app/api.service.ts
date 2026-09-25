import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../environments/environment';
import {
  ApiResponse,
  OrderResponse,
  PortfolioSummaryResponse,
  QuoteResponse,
  RegisterClientRequest,
  SessionResponse,
  SignInRequest,
  SubmitOrderRequest
} from './models';

@Injectable({
  providedIn: 'root'
})
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/api/v1`;

  register(request: RegisterClientRequest): Observable<ApiResponse<unknown>> {
    return this.http.post<ApiResponse<unknown>>(`${this.baseUrl}/auth/register`, request);
  }

  signIn(request: SignInRequest): Observable<ApiResponse<SessionResponse>> {
    return this.http.post<ApiResponse<SessionResponse>>(`${this.baseUrl}/auth/sign-in`, request);
  }

  signOut(token: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/auth/sign-out`, {}, { headers: this.authHeaders(token) });
  }

  getQuote(symbol: string, market: string): Observable<ApiResponse<QuoteResponse>> {
    return this.http.get<ApiResponse<QuoteResponse>>(`${this.baseUrl}/quotes/${encodeURIComponent(symbol)}?market=${encodeURIComponent(market)}`, {
      headers: this.authHeaders(this.requireToken())
    });
  }

  getPortfolio(): Observable<ApiResponse<PortfolioSummaryResponse>> {
    return this.http.get<ApiResponse<PortfolioSummaryResponse>>(`${this.baseUrl}/portfolio/summary`, {
      headers: this.authHeaders(this.requireToken())
    });
  }

  listOrders(): Observable<ApiResponse<OrderResponse[]>> {
    return this.http.get<ApiResponse<OrderResponse[]>>(`${this.baseUrl}/orders`, {
      headers: this.authHeaders(this.requireToken())
    });
  }

  submitOrder(request: SubmitOrderRequest): Observable<ApiResponse<OrderResponse>> {
    return this.http.post<ApiResponse<OrderResponse>>(`${this.baseUrl}/orders`, request, {
      headers: this.authHeaders(this.requireToken())
    });
  }

  private authHeaders(token: string): HttpHeaders {
    return new HttpHeaders({
      Authorization: `Bearer ${token}`
    });
  }

  private requireToken(): string {
    const token = localStorage.getItem('rocketTradingToken');
    if (!token) {
      throw new Error('Sign in first to call the trading API.');
    }
    return token;
  }
}
