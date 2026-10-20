import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthApi, SessionStore, errorMessage } from './api.service';

export const MINIMUM_AGE = 18;
// Anything older is a typo (e.g. 1092 for 1992), not a real client; matches the server's limit
const MAXIMUM_AGE = 120;

/** yyyy-mm-dd for a local calendar date, the format a date input uses. */
function isoDate(date: Date): string {
  return `${String(date.getFullYear()).padStart(4, '0')}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

/** Whole years between a yyyy-mm-dd birth date and today, or null if the text is not a real calendar date. */
export function ageOn(dateOfBirth: string, today: Date): number | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(dateOfBirth);
  if (!match) return null;
  const [year, month, day] = match.slice(1).map(Number);
  const born = new Date(year, month - 1, day);
  // Rejects dates the calendar does not have, such as 31 April
  if (born.getFullYear() !== year || born.getMonth() !== month - 1 || born.getDate() !== day) return null;
  let age = today.getFullYear() - year;
  if (today.getMonth() < month - 1 || (today.getMonth() === month - 1 && today.getDate() < day)) age--;
  return age;
}

/** Why a date of birth cannot be accepted, or null when it is a real date for someone 18 or over. */
export function dateOfBirthProblem(dateOfBirth: string, today = new Date()): string | null {
  if (!dateOfBirth) return 'Enter your date of birth.';
  const age = ageOn(dateOfBirth, today);
  if (age === null || age > MAXIMUM_AGE) return 'Enter a valid date of birth.';
  if (dateOfBirth > isoDate(today)) return 'Date of birth cannot be in the future.';
  if (age < MINIMUM_AGE) return `You must be at least ${MINIMUM_AGE} to open an account.`;
  return null;
}

function yearsAgo(years: number): Date {
  const date = new Date();
  date.setFullYear(date.getFullYear() - years);
  return date;
}

/** First stop after a Google sign-up: Google does not share the details a trading account needs. */
@Component({
  selector: 'app-complete-profile',
  imports: [FormsModule, RouterLink],
  template: `
    <main class="center">
      <form class="card box" (ngSubmit)="save()">
        <h1>Finish setting up your account</h1>
        <p class="hint">Signed in with Google as {{ sessions.session()?.email }}.</p>
        @if (error()) { <p class="alert error" role="alert">{{ error() }}</p> }
        <div class="field">
          <label for="name">Full name</label>
          <input id="name" name="name" class="input" required [(ngModel)]="name" autocomplete="name" />
        </div>
        <div class="field">
          <label for="dob">Date of birth</label>
          <input id="dob" name="dob" class="input" type="date" required [min]="earliestBirthDate" [max]="latestBirthDate"
                 [(ngModel)]="dateOfBirth" aria-describedby="dob-hint" />
          <p id="dob-hint" class="hint small">You must be at least {{ minimumAge }} to trade. We only ask once.</p>
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
    .hint.small { margin: 6px 0 0; font-size: 13px; }
    .skip { display: block; text-align: center; margin-top: 14px; font-size: 14px; font-weight: 600; color: var(--text-muted); }
  `],
})
export class CompleteProfileComponent {
  protected readonly sessions = inject(SessionStore);
  private readonly auth = inject(AuthApi);
  private readonly router = inject(Router);

  protected name = this.sessions.session()?.name ?? '';
  protected dateOfBirth = '';
  protected readonly minimumAge = MINIMUM_AGE;
  protected readonly latestBirthDate = isoDate(yearsAgo(MINIMUM_AGE));
  protected readonly earliestBirthDate = isoDate(yearsAgo(MAXIMUM_AGE));
  protected riskProfile = 'Balanced';
  protected readonly busy = signal(false);
  protected readonly error = signal('');

  save(): void {
    if (!this.name.trim()) {
      this.error.set('Enter your name.');
      return;
    }
    const problem = dateOfBirthProblem(this.dateOfBirth);
    if (problem) {
      this.error.set(problem);
      return;
    }
    this.error.set('');
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
