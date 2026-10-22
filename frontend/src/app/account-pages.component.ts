import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { SessionStore } from './api.service';
import { NavHeaderComponent } from './nav-header.component';

const ACCOUNT_STYLES = `
  .content { max-width: 900px; margin: 0 auto; }
  .back { display: inline-block; color: var(--brand); font-size: 13px; font-weight: 600; margin-bottom: 18px; }
  .back:hover { text-decoration: underline; }
  .intro { margin-bottom: 24px; }
  .intro h1 { font-size: 28px; margin-bottom: 6px; }
  .intro p { color: var(--text-muted); margin: 0; font-size: 14px; }
  .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(360px, 100%), 1fr)); gap: 24px; }
  .card { margin: 0; }
  .card h2 { font-size: 17px; margin-bottom: 18px; }
  .rows { display: grid; gap: 0; }
  .row { display: flex; justify-content: space-between; align-items: flex-start; gap: 20px; padding: 13px 0; border-top: 1px solid var(--border); }
  .row:first-child { border-top: 0; padding-top: 0; }
  .row:last-child { padding-bottom: 0; }
  .row dt { color: var(--text-muted); font-size: 13px; }
  .row dd { margin: 0; text-align: right; font-size: 14px; font-weight: 600; overflow-wrap: anywhere; }
  .row dd .sub { display: block; color: var(--text-muted); font-size: 12px; font-weight: 400; margin-top: 3px; }
  .status { color: var(--amber-text); background: var(--amber-soft); border-radius: 999px; padding: 5px 10px; font-size: 12px; white-space: nowrap; }
  .status.good { color: var(--green); background: var(--green-soft); }
  .action-row { display: flex; align-items: center; justify-content: space-between; gap: 16px; flex-wrap: wrap; padding-top: 18px; margin-top: 18px; border-top: 1px solid var(--border); }
  .danger { color: var(--red); border-color: #e8b9bd; }
  .danger:hover:not(:disabled) { background: var(--red-soft); }
  .notice { margin-top: 16px; }
  .document { display: flex; justify-content: space-between; gap: 16px; align-items: flex-start; }
  .document + .document { border-top: 1px solid var(--border); padding-top: 16px; margin-top: 16px; }
  .document strong { display: block; font-size: 14px; margin-bottom: 4px; }
  .document span { color: var(--text-muted); font-size: 12px; }
  .document .btn { min-height: 36px; padding: 8px 12px; font-size: 12px; white-space: nowrap; }
  .toggle-row { display: flex; justify-content: space-between; gap: 16px; align-items: center; padding: 14px 0; border-top: 1px solid var(--border); }
  .toggle-row:first-child { border-top: 0; padding-top: 0; }
  .toggle-row strong { display: block; font-size: 14px; }
  .toggle-row span { display: block; color: var(--text-muted); font-size: 12px; margin-top: 3px; }
  .toggle { width: 44px; height: 24px; border: 0; border-radius: 999px; background: var(--border); padding: 3px; cursor: pointer; flex: 0 0 auto; }
  .toggle::after { content: ''; display: block; width: 18px; height: 18px; border-radius: 50%; background: #fff; transition: transform .15s ease; }
  .toggle.on { background: var(--brand); }
  .toggle.on::after { transform: translateX(20px); }
  .support-list { display: grid; gap: 12px; margin: 0; padding: 0; list-style: none; }
  .support-list a { color: var(--brand); font-weight: 600; }
  .support-list a:hover { text-decoration: underline; }
  .modal-backdrop { position: fixed; inset: 0; background: rgba(11, 21, 48, .48); z-index: 20; display: grid; place-items: center; padding: 20px; }
  .modal { width: min(440px, 100%); background: var(--surface); border-radius: 16px; padding: 24px; box-shadow: 0 20px 60px rgba(11,21,48,.3); }
  .modal h2 { font-size: 20px; margin-bottom: 8px; }
  .modal p { color: var(--text-muted); font-size: 14px; line-height: 1.5; margin: 0 0 18px; }
  .modal-actions { display: flex; justify-content: flex-end; gap: 10px; }
  @media (max-width: 600px) { .row { display: block; } .row dd { text-align: left; margin-top: 4px; } .document { display: block; } .document .btn { margin-top: 12px; } }
`;

abstract class AccountPage {
  protected readonly sessions = inject(SessionStore);
  protected readonly user = computed(() => this.sessions.session());
}

@Component({
  selector: 'app-profile-settings',
  imports: [FormsModule, RouterLink, NavHeaderComponent],
  template: `
    <app-nav-header />
    <main class="page content">
      <a routerLink="/dashboard" class="back">← Back to dashboard</a>
      <header class="intro"><h1>Profile settings</h1><p>Your account information and verification status.</p></header>
      <section class="card">
        <h2>Personal information</h2>
        <dl class="rows">
          <div class="row"><dt>Full name</dt><dd>{{ user()?.name }}</dd></div>
          <div class="row"><dt>Date of birth</dt><dd>{{ dateOfBirth }}<span class="sub">Update support will be available here soon.</span></dd></div>
          <div class="row"><dt>Email address</dt><dd>{{ user()?.email }}</dd></div>
          <div class="row"><dt>KYC verification</dt><dd><span class="status">Not verified</span></dd></div>
        </dl>
        <div class="action-row"><span class="hint">Need to verify your identity first?</span><a routerLink="/kyc" class="btn btn-primary">Start KYC verification</a></div>
      </section>
      <section class="card">
        <h2>Delete account</h2>
        <p class="hint">This is a placeholder. Account deletion is not connected yet.</p>
        @if (deleteNotice()) { <p class="alert info notice" role="status">{{ deleteNotice() }}</p> }
        <div class="action-row"><span class="hint">This action cannot be undone.</span><button type="button" class="btn btn-outline danger" (click)="deleteOpen.set(true)">Delete account</button></div>
      </section>
    </main>
    @if (deleteOpen()) {
      <div class="modal-backdrop" role="presentation" (click)="closeDelete($event)">
        <section class="modal" role="dialog" aria-modal="true" aria-labelledby="delete-title">
          <h2 id="delete-title">Confirm account deletion</h2>
          <p>Type <strong>{{ user()?.email }}</strong> to confirm. No account changes will be made in this placeholder.</p>
          <div class="field"><label for="delete-email">Email address</label><input id="delete-email" class="input" type="email" name="deleteEmail" [(ngModel)]="deleteEmail" autocomplete="off" /></div>
          @if (deleteError()) { <p class="alert error" role="alert">{{ deleteError() }}</p> }
          <div class="modal-actions"><button type="button" class="btn btn-ghost" (click)="deleteOpen.set(false)">Cancel</button><button type="button" class="btn btn-outline danger" [disabled]="deleteEmail.trim().toLowerCase() !== (user()?.email || '').toLowerCase()" (click)="confirmDelete()">Confirm deletion</button></div>
        </section>
      </div>
    }
  `,
  styles: [ACCOUNT_STYLES],
})
export class ProfileSettingsComponent extends AccountPage {
  protected readonly dateOfBirth = 'Not provided';
  protected readonly deleteOpen = signal(false);
  protected readonly deleteError = signal('');
  protected readonly deleteNotice = signal('');
  protected deleteEmail = '';

  closeDelete(event: MouseEvent): void {
    if (event.target === event.currentTarget) this.deleteOpen.set(false);
  }

  confirmDelete(): void {
    if (this.deleteEmail.trim().toLowerCase() !== (this.user()?.email || '').toLowerCase()) {
      this.deleteError.set('Enter your account email exactly to continue.');
      return;
    }
    this.deleteOpen.set(false);
    this.deleteEmail = '';
    this.deleteNotice.set('Account deletion is not available yet. No changes were made.');
  }
}

@Component({
  selector: 'app-tax-documents',
  imports: [RouterLink, NavHeaderComponent],
  template: `
    <app-nav-header />
    <main class="page content">
      <a routerLink="/dashboard" class="back">← Back to dashboard</a>
      <header class="intro"><h1>Tax documents</h1><p>Placeholder statements for your trading activity.</p></header>
      <section class="card">
        <h2>2025 tax year</h2>
        @for (document of documents; track document.title) {
          <div class="document"><div><strong>{{ document.title }}</strong><span>{{ document.detail }}</span></div><button type="button" class="btn btn-outline" (click)="downloadNotice.set(document.title)">Preview document</button></div>
        }
        @if (downloadNotice()) { <p class="alert info notice" role="status">{{ downloadNotice() }} is a placeholder and is not available for download yet.</p> }
      </section>
      <section class="card"><h2>About your documents</h2><p class="hint">Tax forms and transaction statements are normally prepared after the tax year closes. Your final documents may include proceeds, cost basis, dividends, interest, and digital asset activity.</p></section>
    </main>
  `,
  styles: [ACCOUNT_STYLES],
})
export class TaxDocumentsComponent {
  protected readonly downloadNotice = signal('');
  protected readonly documents = [
    { title: 'Annual trading statement', detail: 'Stocks and ETFs · realized gains and losses · PDF placeholder' },
    { title: 'Digital asset activity report', detail: 'Crypto purchases and sales · cost basis summary · PDF placeholder' },
    { title: 'Dividend and distribution summary', detail: 'ETF and stock distributions · income summary · PDF placeholder' },
  ];
}

@Component({
  selector: 'app-security-settings',
  imports: [RouterLink, NavHeaderComponent],
  template: `
    <app-nav-header />
    <main class="page content">
      <a routerLink="/dashboard" class="back">← Back to dashboard</a>
      <header class="intro"><h1>Security settings</h1><p>Manage sign-in protection, linked accounts, and active sessions.</p></header>
      <div class="grid">
        <section class="card"><h2>Sign-in protection</h2><div class="toggle-row"><div><strong>Biometric authentication</strong><span>Face ID or Touch ID on supported devices</span></div><button type="button" class="toggle" [class.on]="biometric()" [attr.aria-pressed]="biometric()" aria-label="Toggle biometric authentication" (click)="biometric.update(value => !value)"></button></div><div class="row"><dt>Two-factor authentication</dt><dd><span class="status good">Enabled</span><span class="sub">Authentication app</span></dd></div><div class="row"><dt>Available methods</dt><dd>Auth app<span class="sub">Passkeys · SMS or email verification</span></dd></div></section>
        <section class="card"><h2>Linked bank accounts</h2><div class="row"><dt>Rocket Bank •••• 4821</dt><dd><span class="status good">Verified</span><span class="sub">Whitelisted for withdrawals</span></dd></div><div class="row"><dt>External account</dt><dd><span class="status">Not connected</span></dd></div></section>
      </div>
      <section class="card"><h2>Active sessions</h2><div class="row"><dt>This device</dt><dd><span class="status good">Active now</span><span class="sub">Current browser session</span></dd></div><div class="row"><dt>MacBook Pro · Chrome</dt><dd>Last active today<span class="sub">Recognized device</span></dd></div><div class="row"><dt>iPhone 15 · Safari</dt><dd>Last active 3 days ago<span class="sub">Recognized device</span></dd></div><div class="action-row"><span class="hint">Sign out everywhere except this device.</span><button type="button" class="btn btn-outline" (click)="sessionNotice.set('All other sessions will be revoked when this feature is connected.')">Log out of all other devices</button></div>@if (sessionNotice()) { <p class="alert info notice" role="status">{{ sessionNotice() }}</p> }</section>
      <section class="card"><h2>Privacy</h2><div class="toggle-row"><div><strong>Hide balances</strong><span>Replace portfolio values with asterisks on main pages</span></div><button type="button" class="toggle" [class.on]="hideBalances()" [attr.aria-pressed]="hideBalances()" aria-label="Toggle hidden balances" (click)="hideBalances.update(value => !value)"></button></div></section>
    </main>
  `,
  styles: [ACCOUNT_STYLES],
})
export class SecuritySettingsComponent {
  protected readonly biometric = signal(false);
  protected readonly hideBalances = signal(false);
  protected readonly sessionNotice = signal('');
}

@Component({
  selector: 'app-help-support',
  imports: [RouterLink, NavHeaderComponent],
  template: `
    <app-nav-header />
    <main class="page content"><a routerLink="/dashboard" class="back">← Back to dashboard</a><header class="intro"><h1>Help and support</h1><p>Find answers and get help with your Rocket Trading account.</p></header><div class="grid"><section class="card"><h2>Common questions</h2><ul class="support-list"><li><a href="#getting-started">Getting started with trading</a></li><li><a href="#orders">Understanding orders and fills</a></li><li><a routerLink="/kyc">Completing KYC verification</a></li><li><a routerLink="/tax-documents">Finding tax documents</a></li></ul></section><section class="card"><h2>Contact support</h2><p class="hint">Our support team is available Monday to Friday, 9:00 AM to 6:00 PM ET.</p><p><a href="mailto:support@rockettrading.example" class="link">support@rockettrading.example</a></p><button type="button" class="btn btn-primary" (click)="contactNotice.set('Support contact is a placeholder and is not connected yet.')">Start a support request</button>@if (contactNotice()) { <p class="alert info notice" role="status">{{ contactNotice() }}</p> }</section></div><section class="card"><h2>Useful account information</h2><p class="hint">Keep your email up to date, use a unique password, and contact support immediately if you notice a sign-in you do not recognize.</p></section></main>
  `,
  styles: [ACCOUNT_STYLES],
})
export class HelpSupportComponent {
  protected readonly contactNotice = signal('');
}

@Component({
  selector: 'app-kyc-verification',
  imports: [FormsModule, RouterLink, NavHeaderComponent],
  template: `
    <app-nav-header />
    <main class="page content"><a routerLink="/profile-settings" class="back">← Back to profile settings</a><header class="intro"><h1>KYC verification</h1><p>Verify your identity before using all account features.</p></header><section class="card"><div class="row"><dt>Current status</dt><dd><span class="status">Not verified</span></dd></div><p class="hint">This placeholder explains the information normally required. Verification is not connected yet.</p><div class="field"><label for="legal-name">Legal name</label><input id="legal-name" class="input" [value]="user()?.name" readonly /></div><div class="field"><label for="document-type">Identity document</label><select id="document-type" class="input" [(ngModel)]="documentType"><option>Passport</option><option>Driver's license</option><option>National identity card</option></select></div><div class="field"><label for="document-number">Document number</label><input id="document-number" class="input" placeholder="Enter document number" [(ngModel)]="documentNumber" /></div><button type="button" class="btn btn-primary" (click)="notice.set('KYC submission is not available yet. No documents were submitted.')">Submit for verification</button>@if (notice()) { <p class="alert info notice" role="status">{{ notice() }}</p> }</section><section class="card"><h2>What you may need</h2><p class="hint">A valid government-issued identity document, your date of birth, and proof of address may be requested during verification.</p></section></main>
  `,
  styles: [ACCOUNT_STYLES],
})
export class KycVerificationComponent extends AccountPage {
  protected documentType = 'Passport';
  protected documentNumber = '';
  protected readonly notice = signal('');
}
