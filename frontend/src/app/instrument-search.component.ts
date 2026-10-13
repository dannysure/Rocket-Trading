import { Component, computed, inject, input, output, signal } from '@angular/core';
import { switchMap } from 'rxjs';
import { Instrument, MarketApi, errorMessage } from './api.service';
import { displaySymbol, segmentLabel } from './format';

let nextId = 0;

/**
 * Find an instrument by ticker or company name. Anything not yet offered can be looked up with
 * Fauxnance, which adds it to the catalogue if Fauxnance recognizes it.
 */
@Component({
  selector: 'app-instrument-search',
  template: `
    <label [for]="inputId" class="search-label">{{ label() }}</label>
    <div class="box">
      <input [id]="inputId" class="input" type="search" autocomplete="off" [placeholder]="placeholder()"
             [value]="query()" (input)="setQuery($any($event.target).value)"
             (keydown.enter)="$event.preventDefault(); pickFirst()" (keydown.escape)="setQuery('')"
             [attr.aria-describedby]="inputId + '-help'" />
      <span [id]="inputId + '-help'" class="sr-only">Results appear below as you type. Press Enter to pick the first, Escape to clear.</span>
      @if (query().trim()) {
        <div class="dropdown">
          @if (matches().length) {
            <ul class="results" aria-label="Matching instruments">
              @for (m of matches(); track m.symbol) {
                <li>
                  <button type="button" (click)="choose(m)">
                    <span class="bold">{{ displaySymbol(m.symbol) }}</span>
                    <span class="muted name">{{ m.name }}</span>
                    <span class="seg">{{ segmentLabel(m.segment) }}</span>
                  </button>
                </li>
              }
            </ul>
          } @else if (!canLookUp()) {
            <p class="none">No instruments match "{{ query().trim() }}".</p>
          }
          @if (canLookUp()) {
            <button type="button" class="lookup" (click)="lookUp()" [disabled]="lookingUp()">
              {{ lookingUp() ? 'Looking up…' : 'Look up "' + query().trim().toUpperCase() + '" with our price provider' }}
            </button>
          }
          @if (error()) { <p class="alert error" role="alert">{{ error() }}</p> }
        </div>
      }
    </div>
  `,
  styles: [`
    :host { display: block; }
    .search-label { display: block; font-size: 12px; font-weight: 600; color: var(--text-muted); margin-bottom: 6px; }
    .box { position: relative; }
    .dropdown {
      position: absolute; z-index: 20; left: 0; right: 0; top: calc(100% + 6px); padding: 4px;
      background: var(--surface); border: 1px solid var(--border); border-radius: 10px;
      box-shadow: 0 12px 32px rgba(11, 21, 48, .14); max-height: 320px; overflow-y: auto;
    }
    .results { list-style: none; margin: 0; padding: 0; }
    .results button, .lookup {
      width: 100%; display: flex; align-items: baseline; gap: 8px; text-align: left; background: none; border: none;
      border-radius: 8px; padding: 10px; min-height: 44px; cursor: pointer; font-size: 14px; color: var(--text);
    }
    .results button:hover, .results button:focus-visible, .lookup:hover, .lookup:focus-visible { background: var(--surface-alt); }
    .lookup { color: var(--brand); font-weight: 600; }
    .name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .seg { margin-left: auto; font-size: 12px; color: var(--text-muted); white-space: nowrap; }
    .none { margin: 0; padding: 10px; font-size: 14px; color: var(--text-muted); }
    .alert { margin: 6px; }
    .sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); }
  `],
})
export class InstrumentSearchComponent {
  readonly instruments = input.required<Instrument[]>();
  readonly label = input('Search markets');
  readonly placeholder = input('Ticker or company, e.g. MSFT, Tesla, BTC');
  readonly picked = output<Instrument>();
  /** The refreshed catalogue after a look-up added an instrument to it. */
  readonly catalogueChanged = output<Instrument[]>();

  private readonly market = inject(MarketApi);
  protected readonly inputId = `instrument-search-${nextId++}`;
  protected readonly displaySymbol = displaySymbol;
  protected readonly segmentLabel = segmentLabel;

  protected readonly query = signal('');
  protected readonly lookingUp = signal(false);
  protected readonly error = signal('');

  /** Instruments whose ticker or name contains every word typed, tickers that start with it first. */
  protected readonly matches = computed(() => {
    const words = this.query().trim().toLowerCase().split(/\s+/).filter(Boolean);
    if (!words.length) return [];
    const startsWith = (i: Instrument) => displaySymbol(i.symbol).toLowerCase().startsWith(words[0]) ? 0 : 1;
    return this.instruments()
      .filter((i) => {
        const text = `${i.symbol} ${displaySymbol(i.symbol)} ${i.name}`.toLowerCase();
        return words.every((w) => text.includes(w));
      })
      .sort((a, b) => startsWith(a) - startsWith(b))
      .slice(0, 8);
  });

  /** A ticker-shaped query that is not already in the catalogue can be looked up. */
  protected readonly canLookUp = computed(() => {
    const q = this.query().trim().toUpperCase();
    return /^[A-Z0-9.:\-]{1,20}$/.test(q) && !this.instruments().some((i) => i.symbol === q || displaySymbol(i.symbol) === q);
  });

  setQuery(value: string): void {
    this.query.set(value);
    this.error.set('');
  }

  choose(instrument: Instrument): void {
    this.setQuery('');
    this.picked.emit(instrument);
  }

  pickFirst(): void {
    const first = this.matches()[0];
    if (first) this.choose(first);
    else if (this.canLookUp()) this.lookUp();
  }

  /** Pricing a symbol once registers it, if Fauxnance knows it; then it is in the catalogue. */
  lookUp(): void {
    const symbol = this.query().trim().toUpperCase();
    this.lookingUp.set(true);
    this.error.set('');
    this.market.indicative(symbol, 'BUY', 1).pipe(switchMap(() => this.market.instruments())).subscribe({
      next: (list) => {
        this.lookingUp.set(false);
        this.catalogueChanged.emit(list);
        const found = list.find((i) => i.symbol === symbol);
        if (found) this.choose(found);
        else this.error.set(`${symbol} can't be traded here.`);
      },
      error: (e) => {
        this.lookingUp.set(false);
        this.error.set(errorMessage(e, `We couldn't find ${symbol}.`));
      },
    });
  }
}
