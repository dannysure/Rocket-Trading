import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { switchMap } from 'rxjs';
import { AuthApi, SessionStore, errorMessage } from './api.service';
import { BrandMarkComponent } from './brand-mark.component';

type Mode = 'signin' | 'create';

@Component({
  selector: 'app-login',
  imports: [FormsModule, BrandMarkComponent],
  template: `
    <div class="split">
      <aside class="brand-panel">
        <svg class="rings" aria-hidden="true" width="560" height="560" viewBox="0 0 560 560">
          <circle cx="280" cy="280" r="279" fill="none" stroke="#3E6BFF" />
          <circle cx="280" cy="280" r="205" fill="none" stroke="#3E6BFF" />
          <circle cx="280" cy="280" r="130" fill="none" stroke="#3E6BFF" />
        </svg>
        <div class="logo"><app-brand-mark /> <span>Rocket Trading</span></div>
        <div class="pitch">
          <app-brand-mark [size]="76" color="#3E6BFF" />
          <h1>Investing, cleared for launch.</h1>
          <p>See your positions, place a trade and watch it land in your portfolio — no call back required.</p>
        </div>
        <p class="legal">© 2026 Rocket Trading · Built by Team Rocket</p>
      </aside>

      <main class="form-panel">
        <div class="form-box">
          <div class="mode-switch" role="tablist" aria-label="Sign in or create an account">
            <button type="button" role="tab" [attr.aria-selected]="mode() === 'signin'" [class.active]="mode() === 'signin'" (click)="setMode('signin')">Sign in</button>
            <button type="button" role="tab" [attr.aria-selected]="mode() === 'create'" [class.active]="mode() === 'create'" (click)="setMode('create')">Create account</button>
          </div>

          @if (notice()) { <p class="alert info" role="status">{{ notice() }}</p> }
          @if (error()) { <p class="alert error" role="alert">{{ error() }}</p> }

          @if (mode() === 'signin') {
            <h2>Welcome back</h2>
            <p class="lede">Sign in to see your portfolio.</p>
            <form (ngSubmit)="signIn()">
              <div class="field">
                <label for="si-email">Email</label>
                <input id="si-email" class="input" type="email" name="email" autocomplete="email" required [(ngModel)]="email" />
              </div>
              <div class="field">
                <label for="si-pass">Password</label>
                <input id="si-pass" class="input" type="password" name="password" autocomplete="current-password" required [(ngModel)]="password" />
              </div>
              <button type="submit" class="btn btn-primary btn-block" [disabled]="busy()">{{ busy() ? 'Signing in…' : 'Sign in' }}</button>
            </form>
          } @else {
            <h2>Create your account</h2>
            <p class="lede">Open a Rocket Trading account in under a minute.</p>
            <form (ngSubmit)="create()">
              <div class="field">
                <label for="ca-name">Full name</label>
                <input id="ca-name" class="input" type="text" name="name" autocomplete="name" required [(ngModel)]="name" />
              </div>
              <div class="field">
                <label for="ca-email">Email</label>
                <input id="ca-email" class="input" type="email" name="email" autocomplete="email" required [(ngModel)]="email" />
              </div>
              <div class="field">
                <label for="ca-pass">Password</label>
                <input id="ca-pass" class="input" type="password" name="password" autocomplete="new-password" minlength="8" required [(ngModel)]="password" aria-describedby="pass-hint" />
                <span id="pass-hint" class="hint">At least 8 characters.</span>
              </div>
              <button type="submit" class="btn btn-primary btn-block" [disabled]="busy()">{{ busy() ? 'Creating account…' : 'Create account' }}</button>
            </form>
            <p class="small">Your account opens with $10,000 of practice cash. By continuing you agree to the Client Agreement and Risk Disclosure.</p>
          }

          @if (githubEnabled()) {
            <div class="divider"><span>or</span></div>
            <a class="btn btn-outline btn-block" [href]="githubUrl">
              <svg width="18" height="18" viewBox="0 0 16 16" aria-hidden="true"><path fill="currentColor" d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.013 8.013 0 0016 8c0-4.42-3.58-8-8-8z"/></svg>
              Continue with GitHub
            </a>
          }
          @if (googleEnabled()) {
            @if (!githubEnabled()) { <div class="divider"><span>or</span></div> }
            <a class="btn btn-outline btn-block" [href]="googleUrl">
              Continue with Google
            </a>
          }
        </div>
      </main>
    </div>
  `,
  styles: [`
    .split { min-height: 100vh; display: flex; flex-wrap: wrap; }
    .brand-panel {
      flex: 1 1 420px; background: var(--navy-900); color: #fff; padding: 56px; display: flex; flex-direction: column;
      justify-content: space-between; gap: 40px; position: relative; overflow: hidden;
    }
    .rings { position: absolute; right: -180px; bottom: -200px; opacity: .16; }
    .logo { display: flex; align-items: center; gap: 10px; font-family: var(--font-display); font-weight: 700; font-size: 19px; position: relative; }
    .pitch { position: relative; max-width: 400px; }
    .pitch h1 { font-size: 34px; line-height: 1.25; margin-top: 24px; }
    .pitch p { font-size: 15px; line-height: 1.6; color: #c3cce4; margin: 16px 0 0; }
    .legal { position: relative; font-size: 12px; color: #a3aecb; margin: 0; }
    .form-panel { flex: 999 1 480px; display: flex; align-items: center; justify-content: center; padding: 48px 24px; }
    .form-box { width: 100%; max-width: 380px; }
    .mode-switch { display: flex; gap: 4px; background: var(--surface-alt); border-radius: 10px; padding: 4px; margin-bottom: 28px; }
    .mode-switch button {
      flex: 1; padding: 10px 0; border-radius: 8px; border: none; font-weight: 600; font-size: 14px; cursor: pointer;
      background: transparent; color: var(--text-muted); min-height: 40px;
    }
    .mode-switch button.active { background: #fff; color: var(--text); box-shadow: 0 1px 2px rgba(16,24,48,.08); }
    h2 { font-size: 22px; }
    .lede { color: var(--text-muted); font-size: 14px; margin: 6px 0 24px; }
    .small { font-size: 12px; color: var(--text-muted); margin-top: 20px; text-align: center; line-height: 1.5; }
    .divider { display: flex; align-items: center; gap: 12px; margin: 24px 0; color: var(--text-muted); font-size: 13px; }
    .divider::before, .divider::after { content: ''; flex: 1; height: 1px; background: var(--border); }
    @media (max-width: 700px) { .brand-panel { padding: 32px 24px; } .pitch h1 { font-size: 26px; } }
  `],
})
export class LoginComponent implements OnInit {
  private readonly auth = inject(AuthApi);
  private readonly sessions = inject(SessionStore);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly mode = signal<Mode>('signin');
  protected readonly busy = signal(false);
  protected readonly error = signal('');
  protected readonly notice = signal('');
  protected readonly githubEnabled = signal(false);
  protected readonly githubUrl = this.auth.githubSignInUrl();
  protected readonly googleEnabled = signal(false);
  protected readonly googleUrl = this.auth.googleSignInUrl();

  protected name = '';
  protected email = '';
  protected password = '';

  ngOnInit(): void {
    if (this.route.snapshot.queryParamMap.get('expired')) {
      this.notice.set('Your session ended. Please sign in again.');
    }
    this.auth.providers().subscribe({
      next: (p) => {
        this.githubEnabled.set(p.github);
        this.googleEnabled.set(p.google);
      },
      error: () => {
        this.githubEnabled.set(false);
        this.googleEnabled.set(false);
      },
    });
  }

  setMode(mode: Mode): void {
    this.mode.set(mode);
    this.error.set('');
  }

  signIn(): void {
    if (!this.email || !this.password) {
      this.error.set('Enter your email and password.');
      return;
    }
    this.run(this.auth.signIn(this.email.trim(), this.password));
  }

  create(): void {
    if (!this.name.trim() || !this.email.trim() || this.password.length < 8) {
      this.error.set('Enter your name, email and a password of at least 8 characters.');
      return;
    }
    const email = this.email.trim();
    this.run(this.auth.register({ name: this.name.trim(), email, password: this.password })
      .pipe(switchMap(() => this.auth.signIn(email, this.password))));
  }

  private run(request: ReturnType<AuthApi['signIn']>): void {
    this.busy.set(true);
    this.error.set('');
    this.notice.set('');
    request.subscribe({
      next: () => {
        this.busy.set(false);
        this.password = '';
        this.router.navigateByUrl(this.sessions.homeRoute());
      },
      error: (e) => {
        this.busy.set(false);
        this.error.set(errorMessage(e, 'Sign-in failed. Please try again.'));
      },
    });
  }
}
