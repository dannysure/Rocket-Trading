import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthApi, SessionStore, errorMessage } from './api.service';

/** First stop after a GitHub sign-up: GitHub does not share the details a trading account needs. */
@Component({
  selector: 'app-complete-profile',
  imports: [FormsModule, RouterLink],
  template: `
    <main class="center">
      <form class="card box" (ngSubmit)="save()">
        <h1>Finish setting up your account</h1>
        <p class="hint">Signed in with GitHub as {{ sessions.session()?.email }}.</p>
        @if (error()) { <p class="alert error" role="alert">{{ error() }}</p> }
        <div class="field">
          <label for="name">Full name</label>
          <input id="name" name="name" class="input" required [(ngModel)]="name" autocomplete="name" />
        </div>
        <div class="field">
          <label for="dob">Date of birth</label>
          <input id="dob" name="dob" class="input" type="date" required [(ngModel)]="dateOfBirth" />
        </div>
        <div class="field">
          <label for="risk">How much risk are you comfortable with?</label>
          <select id="risk" name="risk" class="input" [(ngModel)]="riskProfile">
            <option value="Cautious">Cautious: protecting what I have matters most</option>
            <option value="Balanced">Balanced: some ups and downs are fine</option>
            <option value="Adventurous">Adventurous: I accept big swings for growth</option>
          </select>
        </div>
        <button type="submit" class="btn btn-primary btn-block" [disabled]="busy()">{{ busy() ? 'Saving…' : 'Continue to my dashboard' }}</button>
        <a routerLink="/dashboard" class="skip">Skip for now</a>
      </form>
    </main>
  `,
  styles: [`
    .center { min-height: 100vh; display: flex; align-items: center; justify-content: center; padding: 24px; }
    .box { max-width: 440px; width: 100%; padding: 32px; margin: 0; }
    h1 { font-size: 22px; }
    .hint { margin: 6px 0 24px; }
    .skip { display: block; text-align: center; margin-top: 14px; font-size: 14px; font-weight: 600; color: var(--text-muted); }
  `],
})
export class CompleteProfileComponent {
  protected readonly sessions = inject(SessionStore);
  private readonly auth = inject(AuthApi);
  private readonly router = inject(Router);

  protected name = this.sessions.session()?.name ?? '';
  protected dateOfBirth = '';
  protected riskProfile = 'Balanced';
  protected readonly busy = signal(false);
  protected readonly error = signal('');

  save(): void {
    if (!this.name.trim() || !this.dateOfBirth) {
      this.error.set('Enter your name and date of birth.');
      return;
    }
    this.busy.set(true);
    this.auth.updateProfile({ name: this.name.trim(), dateOfBirth: this.dateOfBirth, riskProfile: this.riskProfile }).subscribe({
      next: () => this.router.navigate(['/dashboard']),
      error: (e) => {
        this.busy.set(false);
        this.error.set(errorMessage(e, 'Your details could not be saved.'));
      },
    });
  }
}
