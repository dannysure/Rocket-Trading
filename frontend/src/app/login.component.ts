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

          <div class="provider-buttons">
            <a class="btn btn-outline btn-block google-button" [href]="googleUrl">
              <svg class="provider-icon google-icon" aria-hidden="true" viewBox="0 0 24 24">
                <path fill="#4285F4" d="M21.35 12.27c0-.7-.06-1.37-.18-2.02H12v3.83h5.24a4.48 4.48 0 0 1-1.94 2.94v2.45h3.14c1.84-1.69 2.91-4.18 2.91-7.2Z" />
                <path fill="#34A853" d="M12 21.75c2.63 0 4.84-.87 6.45-2.36l-3.14-2.45c-.87.58-1.98.92-3.31.92-2.54 0-4.69-1.72-5.46-4.03H3.3v2.53A9.74 9.74 0 0 0 12 21.75Z" />
                <path fill="#FBBC05" d="M6.54 13.83a5.86 5.86 0 0 1 0-3.66V7.64H3.3a9.75 9.75 0 0 0 0 8.72l3.24-2.53Z" />
                <path fill="#EA4335" d="M12 6.14c1.43 0 2.71.49 3.72 1.45l2.79-2.79C16.84 3.23 14.63 2.25 12 2.25a9.74 9.74 0 0 0-8.7 5.39l3.24 2.53C7.31 7.86 9.46 6.14 12 6.14Z" />
              </svg>
              Continue with Google
            </a>
            <button type="button" class="btn btn-outline btn-block">
              <svg class="provider-icon github-icon" aria-hidden="true" viewBox="0 0 24 24">
                <path d="M12 2.25a9.75 9.75 0 0 0-3.08 19c.49.09.67-.21.67-.47v-1.67c-2.73.59-3.31-1.16-3.31-1.16-.45-1.14-1.1-1.44-1.1-1.44-.89-.61.07-.6.07-.6.98.07 1.5 1 1.5 1 .88 1.5 2.3 1.07 2.86.82.09-.64.34-1.07.62-1.32-2.18-.25-4.47-1.09-4.47-4.86 0-1.07.38-1.94 1-2.62-.1-.25-.43-1.25.1-2.59 0 0 .82-.26 2.68 1a9.3 9.3 0 0 1 4.88 0c1.86-1.26 2.68-1 2.68-1 .53 1.34.2 2.34.1 2.59.62.68 1 1.55 1 2.62 0 3.78-2.3 4.61-4.48 4.86.35.3.66.88.66 1.77v2.62c0 .26.18.56.68.47A9.75 9.75 0 0 0 12 2.25Z" />
              </svg>
              Continue with GitHub
            </button>
            <button type="button" class="btn btn-outline btn-block">
              <svg class="provider-icon apple-icon" aria-hidden="true" viewBox="0 0 24 24">
                <path d="M17.05 12.54c-.02-2.26 1.85-3.35 1.93-3.4a4.15 4.15 0 0 0-3.26-1.76c-1.38-.14-2.7.82-3.4.82-.7 0-1.78-.8-2.93-.78a4.32 4.32 0 0 0-3.64 2.22c-1.57 2.72-.4 6.72 1.1 8.92.75 1.08 1.63 2.29 2.79 2.25 1.12-.05 1.55-.72 2.91-.72s1.75.72 2.92.69c1.21-.02 1.97-1.08 2.7-2.17a8.86 8.86 0 0 0 1.23-2.51 3.89 3.89 0 0 1-2.35-3.56Zm-2.23-6.61a3.96 3.96 0 0 0 .91-2.84 4.02 4.02 0 0 0-2.62 1.36 3.77 3.77 0 0 0-.94 2.73 3.33 3.33 0 0 0 2.65-1.25Z" />
              </svg>
              Continue with Apple
            </button>
          </div>
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
    .provider-buttons { display: flex; flex-direction: column; gap: 12px; }
    .google-button { gap: 10px; }
    .provider-icon { width: 18px; height: 18px; flex: 0 0 18px; }
    .github-icon, .apple-icon { fill: currentColor; }
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
