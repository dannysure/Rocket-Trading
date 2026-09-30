import { Component, inject, onInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink, Router } from '@angular/router';
import { AuthService, RegisterClientRequest } from '../api.service';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="auth-container">
      <div class="card">
        <h1>Register for Rocket Trading</h1>
        
        <form (ngSubmit)="onRegister()" class="form">
          <div class="form-group">
            <label for="name">Full Name</label>
            <input
              id="name"
              type="text"
              [(ngModel)]="form.name"
              name="name"
              class="input"
              required
            />
          </div>

          <div class="form-group">
            <label for="email">Email</label>
            <input
              id="email"
              type="email"
              [(ngModel)]="form.email"
              name="email"
              class="input"
              required
            />
          </div>

          <div class="form-group">
            <label for="dob">Date of Birth</label>
            <input
              id="dob"
              type="date"
              [(ngModel)]="form.dateOfBirth"
              name="dateOfBirth"
              class="input"
              required
            />
          </div>

          <div class="form-group">
            <label for="risk">Risk Profile</label>
            <select
              id="risk"
              [(ngModel)]="form.riskProfile"
              name="riskProfile"
              class="input"
              required
            >
              <option value="Cautious">Cautious</option>
              <option value="Balanced">Balanced</option>
              <option value="Adventurous">Adventurous</option>
            </select>
          </div>

          <button type="submit" class="button button-primary" [disabled]="loading">
            {{ loading ? 'Registering...' : 'Register' }}
          </button>

          <p class="text-center">
            Already registered? <a routerLink="/sign-in">Sign in here</a>
          </p>
        </form>

        <div *ngIf="error" class="error-message">
          {{ error }}
        </div>
        <div *ngIf="success" class="success-message">
          Registration successful! Redirecting to sign in...
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

    .success-message {
      background: #efe;
      color: #3c3;
      padding: 0.75rem;
      border-radius: 4px;
      margin-top: 1rem;
    }
  `]
})
export class RegisterComponent implements onInit {
  private authService = inject(AuthService);
  private router = inject(Router);

  form: RegisterClientRequest = {
    name: '',
    email: '',
    dateOfBirth: '',
    riskProfile: 'Balanced',
  };

  loading = false;
  error = '';
  success = false;

  ngOnInit(): void {
    if (this.authService.isSignedIn()) {
      this.router.navigate(['/dashboard']);
    }
  }

  onRegister(): void {
    if (!this.form.name || !this.form.email || !this.form.dateOfBirth) {
      this.error = 'Please fill in all fields';
      return;
    }

    this.loading = true;
    this.error = '';

    this.authService.register(this.form).subscribe({
      next: (response) => {
        this.success = true;
        this.loading = false;
        setTimeout(() => {
          this.router.navigate(['/sign-in']);
        }, 2000);
      },
      error: (error) => {
        this.error = error.error?.error || 'Registration failed. Please try again.';
        this.loading = false;
      },
    });
  }
}
