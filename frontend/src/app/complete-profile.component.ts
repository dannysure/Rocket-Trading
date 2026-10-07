import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { OAuthService } from './oauth.service';
import { AuthService } from './api.service';

@Component({
  selector: 'app-complete-profile',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="profile-container">
      <div class="profile-card">
        <div class="header-section">
          <h1>Welcome! 👋</h1>
          <p class="subtitle">Complete Your Profile</p>
        </div>

        <form (ngSubmit)="submitProfile()" class="profile-form">
          <div class="form-group">
            <label for="name">Full Name</label>
            <input
              type="text"
              id="name"
              [(ngModel)]="profileData.name"
              name="name"
              placeholder="Your full name"
              class="form-control"
            />
          </div>

          <div class="form-group">
            <label for="email">Email Address</label>
            <input
              type="email"
              id="email"
              [(ngModel)]="profileData.email"
              name="email"
              placeholder="Your email"
              class="form-control"
              disabled
            />
            <small class="help-text">Connected via GitHub OAuth</small>
          </div>

          <div class="form-group">
            <label for="dateOfBirth">Date of Birth *</label>
            <input
              type="date"
              id="dateOfBirth"
              [(ngModel)]="profileData.dateOfBirth"
              name="dateOfBirth"
              class="form-control"
              required
            />
            <small class="help-text">Required for account verification</small>
          </div>

          <div class="form-group">
            <label for="riskProfile">Investment Risk Profile</label>
            <select
              id="riskProfile"
              [(ngModel)]="profileData.riskProfile"
              name="riskProfile"
              class="form-control"
            >
              <option value="Cautious">🛡️ Cautious (Low Risk)</option>
              <option value="Balanced">⚖️ Balanced (Medium Risk)</option>
              <option value="Adventurous">🚀 Adventurous (High Risk)</option>
            </select>
          </div>

          <div *ngIf="errorMessage()" class="alert alert-error">
            {{ errorMessage() }}
          </div>

          <button
            type="submit"
            [disabled]="isSubmitting()"
            class="submit-button"
          >
            {{ isSubmitting() ? 'Saving...' : 'Complete Profile & Continue' }}
          </button>

          <p class="help-text center">
            🔒 Your information is secure and encrypted
          </p>
        </form>
      </div>
    </div>
  `,
  styles: [`
    .profile-container {
      display: flex;
      justify-content: center;
      align-items: center;
      min-height: 100vh;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      padding: 20px;
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
    }

    .profile-card {
      background: white;
      border-radius: 16px;
      box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
      padding: 40px;
      width: 100%;
      max-width: 450px;
    }

    .header-section {
      text-align: center;
      margin-bottom: 32px;
    }

    .header-section h1 {
      color: #333;
      font-size: 28px;
      margin: 0 0 8px 0;
      font-weight: 600;
    }

    .subtitle {
      color: #666;
      font-size: 16px;
      margin: 0;
    }

    .profile-form {
      display: flex;
      flex-direction: column;
      gap: 20px;
    }

    .form-group {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }

    .form-group label {
      color: #333;
      font-weight: 500;
      font-size: 14px;
    }

    .form-control {
      padding: 12px;
      border: 1px solid #ddd;
      border-radius: 8px;
      font-size: 14px;
      font-family: inherit;
      transition: all 0.2s ease;
    }

    .form-control:focus {
      outline: none;
      border-color: #667eea;
      box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
    }

    .form-control:disabled {
      background-color: #f5f5f5;
      color: #999;
      cursor: not-allowed;
    }

    .help-text {
      font-size: 12px;
      color: #999;
      margin: 0;
    }

    .help-text.center {
      text-align: center;
      margin-top: 8px;
    }

    .alert {
      padding: 12px;
      border-radius: 8px;
      font-size: 14px;
      margin: 0;
    }

    .alert-error {
      background-color: #ffebee;
      color: #c62828;
      border-left: 4px solid #c62828;
    }

    .submit-button {
      padding: 12px;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      color: white;
      border: none;
      border-radius: 8px;
      font-size: 16px;
      font-weight: 600;
      cursor: pointer;
      transition: all 0.3s ease;
      margin-top: 8px;
    }

    .submit-button:hover:not(:disabled) {
      transform: translateY(-2px);
      box-shadow: 0 8px 24px rgba(102, 126, 234, 0.4);
    }

    .submit-button:disabled {
      opacity: 0.6;
      cursor: not-allowed;
    }

    @media (max-width: 600px) {
      .profile-card {
        padding: 24px;
      }

      .header-section h1 {
        font-size: 24px;
      }
    }
  `]
})
export class CompleteProfileComponent implements OnInit {
  private oauthService = inject(OAuthService);
  private authService = inject(AuthService);
  private router = inject(Router);

  profileData = {
    name: '',
    email: '',
    dateOfBirth: '',
    riskProfile: 'Balanced'
  };

  // Signals so async updates re-render in the zoneless app
  isSubmitting = signal(false);
  errorMessage = signal('');

  ngOnInit() {
    // Get current user info from OAuth service
    const userInfo = this.oauthService.getUserInfo();
    if (userInfo) {
      this.profileData.name = userInfo.name || '';
      this.profileData.email = userInfo.email || '';
    }
  }

  submitProfile() {
    // Validate required fields
    if (!this.profileData.dateOfBirth) {
      this.errorMessage.set('Date of birth is required');
      return;
    }

    this.isSubmitting.set(true);
    this.errorMessage.set('');

    // Call backend API to update profile
    this.authService.updateProfile({
      name: this.profileData.name,
      dateOfBirth: this.profileData.dateOfBirth,
      riskProfile: this.profileData.riskProfile
    }).subscribe({
      next: (response) => {
        this.isSubmitting.set(false);
        // Redirect to dashboard after successful update
        setTimeout(() => {
          this.router.navigate(['/dashboard']);
        }, 500);
      },
      error: (error) => {
        this.isSubmitting.set(false);
        this.errorMessage.set(error.error?.message || 'Failed to update profile. Please try again.');
        console.error('Profile update error:', error);
      }
    });
  }
}
