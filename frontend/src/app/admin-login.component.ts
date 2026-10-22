import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthApi, SessionStore, errorMessage } from './api.service';
import { BrandMarkComponent } from './brand-mark.component';

@Component({
  selector: 'app-admin-login',
  imports: [FormsModule, RouterLink, BrandMarkComponent],
  template: `
    <div class="admin-login">
      <main class="admin-panel">
        <div class="admin-head">
          <a routerLink="/login" class="brand"><app-brand-mark /><span>Rocket Trading</span></a>
        </div>
        <section class="admin-card">
          <p class="eyebrow">Restricted access</p>
          <h1>Admin sign in</h1>
          <p class="lede">Use your operations or analyst username and password.</p>
          @if (error()) { <p class="alert error" role="alert">{{ error() }}</p> }
          <form (ngSubmit)="signIn()">
            <div class="field">
              <label for="admin-username">Username or email</label>
              <input id="admin-username" class="input" type="text" name="username" autocomplete="username" required [(ngModel)]="username" />
            </div>
            <div class="field">
              <label for="admin-password">Password</label>
              <input id="admin-password" class="input" type="password" name="password" autocomplete="current-password" required [(ngModel)]="password" />
            </div>
            <button type="submit" class="btn btn-primary btn-block" [disabled]="busy()">{{ busy() ? 'Signing in…' : 'Sign in' }}</button>
          </form>
          <a routerLink="/login" class="back-link">← Back to client sign in</a>
        </section>
      </main>
    </div>
  `,
  styles: [`
    .admin-login { min-height: 100vh; display: flex; justify-content: center; padding: 32px 24px; background: var(--bg); }
    .admin-panel { width: 100%; max-width: 480px; }
    .admin-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 56px; }
    .brand { display: inline-flex; align-items: center; gap: 10px; font: 700 19px var(--font-display); }
    .admin-card { padding: 32px; background: var(--surface); border: 1px solid var(--border); border-radius: 16px; }
    .eyebrow { color: var(--brand); font-size: 12px; font-weight: 700; letter-spacing: .06em; margin: 0 0 8px; text-transform: uppercase; }
    h1 { font: 700 28px var(--font-display); margin: 0; }
    .lede { color: var(--text-muted); font-size: 14px; margin: 8px 0 28px; }
    .back-link { display: block; color: var(--brand); font-size: 13px; font-weight: 600; margin-top: 24px; text-align: center; }
    @media (max-width: 520px) { .admin-login { padding: 20px 16px; } .admin-head { margin-bottom: 32px; } .admin-card { padding: 24px; } }
  `],
})
export class AdminLoginComponent {
  private readonly auth = inject(AuthApi);
  private readonly sessions = inject(SessionStore);
  private readonly router = inject(Router);
  protected readonly busy = signal(false);
  protected readonly error = signal('');
  protected username = '';
  protected password = '';

  signIn(): void {
    if (!this.username.trim() || !this.password) {
      this.error.set('Enter your username and password.');
      return;
    }
    this.busy.set(true);
    this.error.set('');
    this.auth.signIn(this.username.trim(), this.password).subscribe({
      next: (session) => {
        this.busy.set(false);
        this.password = '';
        if (session.role === 'ANALYST' || session.role === 'OPERATIONS') {
          this.router.navigateByUrl(this.sessions.homeRoute());
          return;
        }
        this.auth.signOut().subscribe({ complete: () => this.rejectClient(), error: () => this.rejectClient() });
      },
      error: (error) => {
        this.busy.set(false);
        this.error.set(errorMessage(error, 'Staff sign-in failed. Please try again.'));
      },
    });
  }

  private rejectClient(): void {
    this.password = '';
    this.error.set('This sign-in is for staff accounts only.');
  }
}
