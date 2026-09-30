import { Injectable, inject, signal } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, BehaviorSubject } from 'rxjs';

const API_BASE_URL = 'http://localhost:8081/api/v1';
const OAUTH_REDIRECT_URI = 'http://localhost:4200/auth/callback';
const SESSION_STORAGE_KEY = 'rocket-trading-session';

export interface OAuthSession {
  clientId: number;
  email: string;
  name: string;
  token: string;
  provider: string;
  expiresAt: string;
}

export interface OAuthLoginResponse {
  success: boolean;
  clientId?: number;
  email?: string;
  name?: string;
  accessToken?: string;
  tokenType?: string;
  expiresIn?: number;
  provider?: string;
  error?: string;
}

export interface OAuthProvider {
  id: string;
  name: string;
  icon: string;
  color: string;
}

@Injectable({ providedIn: 'root' })
export class OAuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly sessionState = signal<OAuthSession | null>(this.readSession());
  readonly session = this.sessionState.asReadonly();
  readonly isSignedIn = signal(this.sessionState() !== null);

  // Supported OAuth providers
  readonly oauthProviders: OAuthProvider[] = [
    {
      id: 'google',
      name: 'Google',
      icon: '🔍',
      color: '#4285F4'
    },
    {
      id: 'github',
      name: 'GitHub',
      icon: '🐙',
      color: '#333'
    },
    {
      id: 'microsoft',
      name: 'Microsoft',
      icon: '⊞',
      color: '#0078D4'
    }
  ];

  /**
   * Initiate OAuth login flow
   * Redirects user to OAuth provider authorization endpoint
   */
  initiateOAuthLogin(provider: string): void {
    const authEndpoint = `${API_BASE_URL}/auth/oauth2/authorization/${provider}`;
    const params = new URLSearchParams({
      response_type: 'code',
      client_id: `oauth-${provider}`,
      redirect_uri: OAUTH_REDIRECT_URI,
      scope: 'openid profile email',
      state: this.generateState()
    });

    window.location.href = `${authEndpoint}?${params.toString()}`;
  }

  /**
   * Handle OAuth callback
   * Called when user is redirected back from OAuth provider
   * Expects token in URL as query parameter
   */
  handleOAuthCallback(token: string): Observable<OAuthLoginResponse> {
    return new Observable(observer => {
      if (!token) {
        observer.error(new Error('No token received from OAuth provider'));
        return;
      }

      // Exchange token for session
      this.exchangeTokenForSession(token).subscribe({
        next: (response) => {
          if (response.success && response.accessToken) {
            this.writeSession({
              clientId: response.clientId!,
              email: response.email!,
              name: response.name!,
              token: response.accessToken,
              provider: response.provider || 'unknown',
              expiresAt: new Date(Date.now() + (response.expiresIn || 28800) * 1000).toISOString()
            });

            this.sessionState.set(this.readSession());
            this.isSignedIn.set(true);
            observer.next(response);
          } else {
            observer.error(new Error(response.error || 'OAuth exchange failed'));
          }
        },
        error: (err) => observer.error(err)
      });
    });
  }

  /**
   * Exchange OAuth token for application JWT token
   */
  private exchangeTokenForSession(oauthToken: string): Observable<OAuthLoginResponse> {
    const headers = new HttpHeaders({
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${oauthToken}`
    });

    return this.http.post<OAuthLoginResponse>(
      `${API_BASE_URL}/auth/oauth/success`,
      {},
      { headers }
    );
  }

  /**
   * Get current user profile
   */
  getCurrentUser(): Observable<any> {
    const headers = this.getAuthHeaders();
    return this.http.get(`${API_BASE_URL}/auth/me`, { headers });
  }

  /**
   * Sign out / Logout
   */
  signOut(): Observable<any> {
    return new Observable(observer => {
      const headers = this.getAuthHeaders();
      this.http.post(
        `${API_BASE_URL}/auth/sign-out`,
        {},
        { headers }
      ).subscribe({
        next: () => {
          this.clearSession();
          this.sessionState.set(null);
          this.isSignedIn.set(false);
          observer.next({ success: true });
        },
        error: (err) => {
          // Clear session even if sign-out fails on server
          this.clearSession();
          this.sessionState.set(null);
          this.isSignedIn.set(false);
          observer.error(err);
        }
      });
    });
  }

  /**
   * Get authorization headers for API calls
   */
  getAuthHeaders(): HttpHeaders {
    const session = this.readSession();
    if (!session) {
      return new HttpHeaders();
    }
    return new HttpHeaders({
      'Authorization': `Bearer ${session.token}`,
      'Content-Type': 'application/json'
    });
  }

  /**
   * Check if user is authenticated
   */
  isAuthenticated(): boolean {
    const session = this.readSession();
    if (!session) return false;

    // Check if token is expired
    const expiresAt = new Date(session.expiresAt);
    return expiresAt > new Date();
  }

  /**
   * Get stored session from sessionStorage
   */
  private readSession(): OAuthSession | null {
    try {
      const stored = sessionStorage.getItem(SESSION_STORAGE_KEY);
      if (!stored) return null;
      return JSON.parse(stored);
    } catch (err) {
      console.error('Failed to read session from storage', err);
      return null;
    }
  }

  /**
   * Store session in sessionStorage
   */
  private writeSession(session: OAuthSession | null): void {
    try {
      if (session === null) {
        sessionStorage.removeItem(SESSION_STORAGE_KEY);
      } else {
        sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(session));
      }
    } catch (err) {
      console.error('Failed to write session to storage', err);
    }
  }

  /**
   * Clear session
   */
  private clearSession(): void {
    this.writeSession(null);
  }

  /**
   * Generate random state for CSRF protection
   */
  private generateState(): string {
    return Math.random().toString(36).substring(2, 15) +
           Math.random().toString(36).substring(2, 15);
  }
}
