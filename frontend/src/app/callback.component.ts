import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { OAuthService } from './oauth.service';

@Component({
  selector: 'app-auth-callback',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="callback-container">
      <div class="callback-card">
        <div class="spinner"></div>
        <h2>{{ message }}</h2>
        <p *ngIf="status === 'processing'" class="status-text">
          Please wait while we authenticate your account...
        </p>
        <p *ngIf="status === 'success'" class="status-text success">
          Authentication successful! Redirecting to dashboard...
        </p>
        <div *ngIf="status === 'error'" class="error-content">
          <p class="status-text error">{{ errorMessage }}</p>
          <button class="retry-button" (click)="retryLogin()">
            Try Again
          </button>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .callback-container {
      display: flex;
      justify-content: center;
      align-items: center;
      min-height: 100vh;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
    }

    .callback-card {
      background: white;
      border-radius: 12px;
      box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
      padding: 60px 40px;
      width: 100%;
      max-width: 400px;
      text-align: center;
    }

    .spinner {
      width: 50px;
      height: 50px;
      border: 4px solid #e0e0e0;
      border-top-color: #667eea;
      border-radius: 50%;
      animation: spin 1s linear infinite;
      margin: 0 auto 24px;
    }

    @keyframes spin {
      to { transform: rotate(360deg); }
    }

    h2 {
      color: #333;
      margin: 0 0 16px 0;
      font-size: 20px;
    }

    .status-text {
      color: #666;
      font-size: 14px;
      margin: 0;
      line-height: 1.5;
    }

    .status-text.success {
      color: #4caf50;
      font-weight: 500;
    }

    .status-text.error {
      color: #f44336;
    }

    .error-content {
      margin-top: 16px;
    }

    .retry-button {
      margin-top: 16px;
      padding: 10px 20px;
      background: #667eea;
      color: white;
      border: none;
      border-radius: 6px;
      font-size: 14px;
      font-weight: 500;
      cursor: pointer;
      transition: all 0.2s ease;
    }

    .retry-button:hover {
      background: #764ba2;
      transform: translateY(-2px);
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
    }
  `]
})
export class CallbackComponent implements OnInit {
  private oauthService = inject(OAuthService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  status: 'processing' | 'success' | 'error' = 'processing';
  message = 'Signing you in...';
  errorMessage = '';

  ngOnInit() {
    this.processOAuthCallback();
  }

  private processOAuthCallback() {
    // Get token from URL query parameter
    this.route.queryParams.subscribe(params => {
      const token = params['token'];
      const error = params['error'];

      if (error) {
        this.handleError(`Authentication failed: ${error}`);
        return;
      }

      if (!token) {
        this.handleError('No authentication token received. Please try again.');
        return;
      }

      // Exchange token for session
      this.oauthService.handleOAuthCallback(token).subscribe({
        next: (response) => {
          this.status = 'success';
          this.message = `Welcome, ${response.name}!`;
          
          // Redirect to profile completion page instead of dashboard
          // User needs to complete their profile (date of birth, etc.)
          setTimeout(() => {
            this.router.navigate(['/complete-profile']);
          }, 1000);
        },
        error: (err) => {
          const errorMsg = err?.error?.error || err?.message || 'Authentication failed';
          this.handleError(errorMsg);
        }
      });
    });
  }

  private handleError(message: string) {
    this.status = 'error';
    this.message = 'Authentication Failed';
    this.errorMessage = message;
  }

  retryLogin() {
    this.router.navigate(['/login']);
  }
}
