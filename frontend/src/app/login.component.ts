import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AuthApi } from './api.service';
import { BrandMarkComponent } from './brand-mark.component';

@Component({
  selector: 'app-login',
  imports: [RouterLink, BrandMarkComponent],
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
          @if (notice()) { <p class="alert info" role="status">{{ notice() }}</p> }
          @if (error()) { <p class="alert error" role="alert">{{ error() }}</p> }

          <h2>Welcome to Rocket Trading</h2>
          <p class="lede">Sign in with a connected provider to access your portfolio.</p>

          <a class="btn btn-outline btn-block google-button" [href]="googleUrl">
            <span class="google-mark" aria-hidden="true">G</span>
            Continue with Google
          </a>
          <a routerLink="/admin/login" class="staff-link">Admin sign in</a>
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
    h2 { font-size: 22px; }
    .lede { color: var(--text-muted); font-size: 14px; margin: 6px 0 24px; }
    .small { font-size: 12px; color: var(--text-muted); margin-top: 20px; text-align: center; line-height: 1.5; }
    .staff-link { display: block; color: var(--text-muted); font-size: 12px; margin-top: 24px; text-align: center; }
    .staff-link:hover { color: var(--brand); }
    .google-button { gap: 10px; }
    .google-mark { color: #4285f4; font-size: 18px; font-weight: 700; }
    .divider { display: flex; align-items: center; gap: 12px; margin: 24px 0; color: var(--text-muted); font-size: 13px; }
    .divider::before, .divider::after { content: ''; flex: 1; height: 1px; background: var(--border); }
    @media (max-width: 700px) { .brand-panel { padding: 32px 24px; } .pitch h1 { font-size: 26px; } }
  `],
})
export class LoginComponent implements OnInit {
  private readonly auth = inject(AuthApi);
  private readonly route = inject(ActivatedRoute);

  protected readonly error = signal('');
  protected readonly notice = signal('');
  protected readonly googleUrl = this.auth.googleSignInUrl();

  ngOnInit(): void {
    if (this.route.snapshot.queryParamMap.get('expired')) {
      this.notice.set('Your session ended. Please sign in again.');
    }
  }

}
