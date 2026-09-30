import { Component, inject, onInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink, Router } from '@angular/router';
import { AuthService } from '../api.service';

@Component({
  selector: 'app-sign-in',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="auth-container">
      <div class="card">
        <h1>Sign In to Rocket Trading</h1>
        
        <form (ngSubmit)="onSignIn()" class="form">
          <div class="form-group">
            <label for="email">Email</label>
            <input
              id="email"
              type="email"
              [(ngModel)]="email"
              name="email"
              class="input"
              placeholder="your@email.com"
              required
            />
          </div>

          <button type="submit" class="button button-primary" [disabled]="loading">
            {{ loading ? 'Signing in...' : 'Sign In' }}
          </button>

          <p class="text-center">
            New to Rocket Trading? <a routerLink="/register">Register here</a>
          </p>
        </form>

        <div *ngIf="error" class="error-message">
          {{ error }}
        </div>
      </div>
    </div>
  `,
  styles: [`
    .auth-container {
      display: flex;
      justify-content: center;
      align-items: center;
      min-height: 100vh;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    }

    .card {
      background: white;
      padding: 2rem;
      border-radius: 8px;
      box-shadow: 0 10px 25px rgba(0, 0, 0, 0.1);
      width: 100%;
      max-width: 400px;
    }

    h1 {
      margin-bottom: 1.5rem;
      text-align: center;
      color: #333;
    }

    .form-group {
      margin-bottom: 1rem;
    }

    label {
      display: block;
      margin-bottom: 0.5rem;
      font-weight: 500;
      color: #555;
    }

    .input {
      width: 100%;
      padding: 0.75rem;
      border: 1px solid #ddd;
      border-radius: 4px;
      font-size: 1rem;
      box-sizing: border-box;
    }

    .input:focus {
      outline: none;
      border-color: #667eea;
      box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
    }

    .button {
      width: 100%;
      padding: 0.75rem;
      border: none;
      border-radius: 4px;
      font-size: 1rem;
      font-weight: 500;
      cursor: pointer;
      margin-top: 1rem;
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

    .text-center {
      text-align: center;
      margin-top: 1rem;
      font-size: 0.9rem;
    }

    a {
      color: #667eea;
      text-decoration: none;
    }

    a:hover {
      text-decoration: underline;
    }

    .error-message {
      background: #fee;
      color: #c33;
      padding: 0.75rem;
      border-radius: 4px;
      margin-top: 1rem;
    }
  `]
})
export class SignInComponent implements onInit {
  private authService = inject(AuthService);
  private router = inject(Router);

  email = '';
  loading = false;
  error = '';

  ngOnInit(): void {
    if (this.authService.isSignedIn()) {
      this.router.navigate(['/dashboard']);
    }
  }

  onSignIn(): void {
    if (!this.email) {
      this.error = 'Please enter your email';
      return;
    }

    this.loading = true;
    this.error = '';

    this.authService.signIn(this.email).subscribe({
      next: (response) => {
        this.loading = false;
        this.router.navigate(['/dashboard']);
      },
      error: (error) => {
        this.error = error.error?.error || 'Sign-in failed. Please try again.';
        this.loading = false;
      },
    });
  }
}
