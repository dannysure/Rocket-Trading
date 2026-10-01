import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { OAuthService } from './oauth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="login-container">
      <div class="login-card">
        <div class="logo-section">
          <h1>🚀 Rocket Trading</h1>
          <p class="subtitle">Advanced Trading Platform</p>
        </div>

        <div class="login-section">
          <h2>Sign In to Your Account</h2>
          <p class="description">Choose your preferred authentication method</p>

          <div class="oauth-providers">
            <button
              *ngFor="let provider of oauthProviders"
              class="provider-button"
              [style.border-color]="provider.color"
              (click)="loginWithProvider(provider.id)"
              [attr.aria-label]="'Sign in with ' + provider.name"
            >
              <span class="provider-icon">{{ provider.icon }}</span>
              <span class="provider-name">Sign in with {{ provider.name }}</span>
            </button>
          </div>

          <div class="divider">
            <span>New to Rocket Trading?</span>
          </div>

          <p class="signup-text">
            Sign up is automatic when you authenticate with any provider above.
            We never store passwords—authentication is handled securely by your provider.
          </p>
        </div>

        <div class="features-section">
          <h3>Why Rocket Trading?</h3>
          <ul class="features-list">
            <li>✅ Secure OAuth authentication (no passwords to manage)</li>
            <li>✅ Real-time portfolio tracking</li>
            <li>✅ Advanced order management</li>
            <li>✅ Professional trading tools</li>
            <li>✅ 24/7 market access</li>
          </ul>
        </div>

        <div class="footer-section">
          <p class="footer-text">
            <a href="#">Privacy Policy</a> • 
            <a href="#">Terms of Service</a> • 
            <a href="#">Contact Support</a>
          </p>
        </div>
      </div>

      <div class="demo-section">
        <h3>Demo Credentials Available</h3>
        <p>Use any OAuth provider to create an account instantly with demo data</p>
      </div>
    </div>
  `,
  styles: [`
    .login-container {
      display: flex;
      justify-content: center;
      align-items: center;
      min-height: 100vh;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
    }

    .login-card {
      background: white;
      border-radius: 12px;
      box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
      padding: 40px;
      width: 100%;
      max-width: 450px;
      animation: slideIn 0.3s ease-out;
    }

    @keyframes slideIn {
      from {
        opacity: 0;
        transform: translateY(20px);
      }
      to {
        opacity: 1;
        transform: translateY(0);
      }
    }

    .logo-section {
      text-align: center;
      margin-bottom: 40px;
    }

    .logo-section h1 {
      font-size: 32px;
      margin: 0;
      color: #333;
    }

    .subtitle {
      color: #666;
      margin: 8px 0 0 0;
      font-size: 14px;
    }

    .login-section {
      margin-bottom: 40px;
    }

    .login-section h2 {
      font-size: 24px;
      margin: 0 0 8px 0;
      color: #333;
    }

    .description {
      color: #666;
      font-size: 14px;
      margin: 0 0 24px 0;
    }

    .oauth-providers {
      display: flex;
      flex-direction: column;
      gap: 12px;
      margin-bottom: 24px;
    }

    .provider-button {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 12px 16px;
      border: 2px solid #e0e0e0;
      border-radius: 8px;
      background: white;
      cursor: pointer;
      font-size: 16px;
      font-weight: 500;
      transition: all 0.2s ease;
    }

    .provider-button:hover {
      background: #f5f5f5;
      transform: translateY(-2px);
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
    }

    .provider-icon {
      font-size: 20px;
    }

    .provider-name {
      flex: 1;
      text-align: left;
      color: #333;
    }

    .divider {
      text-align: center;
      margin: 24px 0;
      position: relative;
    }

    .divider span {
      background: white;
      padding: 0 12px;
      color: #999;
      font-size: 13px;
      position: relative;
      z-index: 1;
    }

    .divider::before {
      content: '';
      position: absolute;
      top: 50%;
      left: 0;
      right: 0;
      height: 1px;
      background: #e0e0e0;
    }

    .signup-text {
      color: #666;
      font-size: 13px;
      margin: 0;
      line-height: 1.5;
    }

    .features-section {
      margin-top: 32px;
      padding-top: 24px;
      border-top: 1px solid #e0e0e0;
    }

    .features-section h3 {
      font-size: 14px;
      color: #333;
      margin: 0 0 12px 0;
      font-weight: 600;
    }

    .features-list {
      list-style: none;
      padding: 0;
      margin: 0;
    }

    .features-list li {
      color: #666;
      font-size: 13px;
      margin: 8px 0;
      line-height: 1.4;
    }

    .footer-section {
      margin-top: 24px;
      text-align: center;
    }

    .footer-text {
      color: #999;
      font-size: 12px;
      margin: 0;
    }

    .footer-text a {
      color: #667eea;
      text-decoration: none;
    }

    .footer-text a:hover {
      text-decoration: underline;
    }

    .demo-section {
      position: absolute;
      bottom: 40px;
      left: 40px;
      color: white;
      font-size: 12px;
    }

    .demo-section h3 {
      margin: 0 0 4px 0;
      font-size: 12px;
    }

    .demo-section p {
      margin: 0;
    }

    @media (max-width: 600px) {
      .login-container {
        padding: 20px;
      }

      .login-card {
        padding: 24px;
      }

      .logo-section h1 {
        font-size: 24px;
      }

      .demo-section {
        position: static;
        margin-top: 24px;
        padding-top: 24px;
        border-top: 1px solid rgba(255, 255, 255, 0.2);
      }
    }
  `]
})
export class LoginComponent {
  private oauthService = inject(OAuthService);
  private router = inject(Router);

  oauthProviders = this.oauthService.oauthProviders;

  ngOnInit() {
    // Redirect if already signed in
    if (this.oauthService.isSignedIn()) {
      this.router.navigate(['/dashboard']);
    }
  }

  loginWithProvider(provider: string) {
    this.oauthService.initiateOAuthLogin(provider);
  }
}
