import { Component } from '@angular/core';

@Component({
  selector: 'app-portfolio-page',
  standalone: true,
  template: `
    <main class="page subpage">
      <p class="section-label">Holdings overview</p>
      <h1>Portfolio</h1>
      <p class="lead">
        Review allocations, balances, and performance trends with a modern portfolio view
        that stays fast and easy to scan.
      </p>
    </main>
  `,
})
export class PortfolioPageComponent {}
