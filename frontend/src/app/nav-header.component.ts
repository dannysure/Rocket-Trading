import { Component, computed, inject, input } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { AuthApi, SessionStore } from './api.service';
import { BrandMarkComponent } from './brand-mark.component';
import { money } from './format';

/** Header for both apps: the client app (Dashboard, Markets, Blotter) and the internal back office. */
@Component({
  selector: 'app-nav-header',
  imports: [RouterLink, RouterLinkActive, BrandMarkComponent],
  template: `
    <header class="nav">
      <div class="nav-left">
        <a [routerLink]="sessions.homeRoute()" class="brand">
          <app-brand-mark />
          <span class="wordmark">Rocket Trading</span>
          @if (internal()) { <span class="internal-badge">Internal</span> }
        </a>
        <nav class="links" aria-label="Primary">
          @if (internal()) {
            <a routerLink="/insights" routerLinkActive="active" class="tab">Insights</a>
            @if (sessions.role() === 'OPERATIONS') {
              <a routerLink="/ops" routerLinkActive="active" class="tab">Trade lookup</a>
            }
          } @else {
            <a routerLink="/dashboard" routerLinkActive="active" class="tab">Dashboard</a>
            <a routerLink="/trade" routerLinkActive="active" class="tab">Markets</a>
            <a routerLink="/blotter" routerLinkActive="active" class="tab">Blotter</a>
          }
        </nav>
      </div>
      <div class="nav-right">
        @if (cash() !== null) {
          <span class="cash-pill">Cash <span class="num">{{ cashText() }}</span></span>
        }
        <span class="who">
          <span class="avatar" aria-hidden="true">{{ initial() }}</span>
          <span class="who-text">
            <span class="who-name">{{ sessions.session()?.name }}</span>
            @if (internal()) { <span class="who-role">{{ roleLabel() }}</span> }
          </span>
        </span>
        <button type="button" class="sign-out" (click)="signOut()">Sign out</button>
      </div>
    </header>
  `,
  styles: [`
    .nav {
      background: var(--navy-900); color: #fff; display: flex; align-items: center; justify-content: space-between;
      gap: 16px 24px; padding: 12px 40px; min-height: 72px; flex-wrap: wrap;
    }
    .nav-left { display: flex; align-items: center; gap: 36px; flex-wrap: wrap; }
    .brand { display: flex; align-items: center; gap: 10px; }
    .wordmark { font-family: var(--font-display); font-weight: 700; font-size: 18px; letter-spacing: -0.01em; }
    .internal-badge {
      font-size: 11px; font-weight: 700; text-transform: uppercase; letter-spacing: .05em;
      background: var(--navy-800); color: #c9d3ec; padding: 4px 9px; border-radius: 999px;
    }
    .links { display: flex; gap: 28px; }
    .tab { font-size: 14px; font-weight: 600; color: #c9d3ec; padding: 8px 2px; border-bottom: 2px solid transparent; }
    .tab:hover { color: #fff; }
    .tab.active { color: #fff; border-bottom-color: #5b82ff; }
    .nav-right { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; }
    .cash-pill { background: var(--navy-800); border-radius: 999px; padding: 8px 14px; font-size: 13px; font-weight: 600; color: #c9d3ec; }
    .cash-pill .num { color: #fff; }
    .who { display: flex; align-items: center; gap: 10px; }
    .avatar {
      width: 32px; height: 32px; border-radius: 999px; background: var(--brand); display: flex;
      align-items: center; justify-content: center; font-weight: 700; font-size: 13px;
    }
    .who-text { display: flex; flex-direction: column; line-height: 1.2; }
    .who-name { font-size: 13px; font-weight: 600; }
    .who-role { font-size: 11px; color: #c9d3ec; }
    .sign-out {
      background: transparent; border: 1px solid #33406a; color: #c9d3ec; border-radius: 8px;
      padding: 8px 12px; font-size: 13px; font-weight: 600; cursor: pointer; min-height: 36px;
    }
    .sign-out:hover { color: #fff; border-color: #5b82ff; }
    @media (max-width: 700px) {
      .nav { padding: 12px 16px; }
      .who-text { display: none; }
    }
  `],
})
export class NavHeaderComponent {
  readonly internal = input(false);
  readonly cash = input<number | null>(null);
  readonly currency = input('USD');

  protected readonly sessions = inject(SessionStore);
  private readonly auth = inject(AuthApi);
  private readonly router = inject(Router);

  protected readonly cashText = computed(() => money(this.cash(), this.currency()));
  protected readonly initial = computed(() => (this.sessions.session()?.name || '?').charAt(0).toUpperCase());
  protected readonly roleLabel = computed(() =>
    this.sessions.role() === 'OPERATIONS' ? 'Trading operations' : 'Commercial analyst');

  signOut(): void {
    this.auth.signOut().subscribe({
      complete: () => this.router.navigate(['/login']),
      error: () => this.router.navigate(['/login']),
    });
  }
}
