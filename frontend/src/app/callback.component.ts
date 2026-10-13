import { Component, OnInit, PLATFORM_ID, inject, signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthApi, SessionStore, errorMessage } from './api.service';
import { BrandMarkComponent } from './brand-mark.component';

/** Landing page for the GitHub redirect: swaps the one-time token for a session. */
@Component({
  selector: 'app-auth-callback',
  imports: [RouterLink, BrandMarkComponent],
  template: `
    <main class="center">
      <div class="card box">
        <app-brand-mark [size]="48" color="#1F49E6" />
        @if (failure()) {
          <h1>Sign-in didn't complete</h1>
          <p class="alert error" role="alert">{{ failure() }}</p>
          <a routerLink="/login" class="btn btn-primary btn-block">Back to sign in</a>
        } @else {
          <h1>Signing you in…</h1>
          <p class="hint" role="status">Confirming your GitHub account.</p>
        }
      </div>
    </main>
  `,
  styles: [`
    .center { min-height: 100vh; display: flex; align-items: center; justify-content: center; padding: 24px; background: var(--navy-900); }
    .box { max-width: 400px; width: 100%; text-align: center; padding: 40px 32px; margin: 0; }
    h1 { font-size: 20px; margin: 16px 0 8px; }
    .alert { margin: 16px 0; }
  `],
})
export class CallbackComponent implements OnInit {
  private readonly auth = inject(AuthApi);
  private readonly sessions = inject(SessionStore);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly platformId = inject(PLATFORM_ID);

  protected readonly failure = signal('');

  ngOnInit(): void {
    if (!isPlatformBrowser(this.platformId)) return;
    const params = this.route.snapshot.queryParamMap;
    const token = params.get('token');
    if (params.get('error') || !token) {
      this.failure.set(params.get('error') || 'No sign-in token was received from GitHub.');
      return;
    }
    // Drop the token from the address bar and history before using it
    history.replaceState(null, '', '/auth/callback');
    this.auth.completeOAuth(token).subscribe({
      next: (session) => this.router.navigateByUrl(session.role === 'CLIENT' ? '/complete-profile' : this.sessions.homeRoute()),
      error: (e) => this.failure.set(errorMessage(e, 'GitHub sign-in failed.')),
    });
  }
}
