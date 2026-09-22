import { Component } from '@angular/core';

@Component({
  selector: 'app-home-page',
  standalone: true,
  template: `
    <main class="page">
      <section class="hero">
        <div class="hero-copy">
          <p class="section-label">Modern trading, simplified</p>
          <h1>Rocket Trading Platform</h1>
          <p class="lead">
            RTP brings together portfolio management, market news, advanced charts, and
            perpetual futures in a clean interface built for fast, informed decisions.
          </p>

          <div class="hero-actions">
            <a class="button button-primary" href="#platform-overview">Open Workspace</a>
            <a class="button button-secondary" href="#why-rtp">Explore Features</a>
          </div>
        </div>

        <aside class="market-card" aria-label="Market snapshot">
          <h2>Live Market Pulse</h2>
          <div class="metric-row">
            <span>Dow Jones</span>
            <strong>39,842.18</strong>
            <span class="metric-up">+1.12%</span>
          </div>
          <div class="metric-row">
            <span>S&amp;P 500</span>
            <strong>5,476.30</strong>
            <span class="metric-up">+0.84%</span>
          </div>
          <div class="metric-row">
            <span>Nasdaq</span>
            <strong>17,991.52</strong>
            <span class="metric-up">+1.48%</span>
          </div>
          <div class="metric-row">
            <span>10Y Treasury</span>
            <strong>4.11%</strong>
            <span class="metric-down">-0.06%</span>
          </div>
        </aside>
      </section>

      <section class="feature-grid" id="why-rtp">
        <article class="feature-card">
          <h2>Portfolio command</h2>
          <p>Track allocations, balances, and performance from a central modern workspace.</p>
        </article>
        <article class="feature-card">
          <h2>News stream</h2>
          <p>Read timely headlines and market-moving developments without leaving the platform.</p>
        </article>
        <article class="feature-card">
          <h2>Chart analysis</h2>
          <p>Move from watchlists to chart views quickly with a layout built around clarity.</p>
        </article>
        <article class="feature-card">
          <h2>Perpetual futures</h2>
          <p>Monitor leveraged products, funding, and positioning with the same streamlined design.</p>
        </article>
      </section>

      <section class="overview-panel" id="platform-overview">
        <div>
          <p class="section-label">One interface for every workflow</p>
          <h2>A lighter, more modern trading homepage</h2>
        </div>

        <div class="overview-columns">
          <div>
            <h3>What you can do</h3>
            <ul>
              <li>Review your portfolio and open positions at a glance</li>
              <li>Switch from live news to charts in a single navigation flow</li>
              <li>Manage spot and perpetual futures activity from one platform</li>
            </ul>
          </div>

          <div>
            <h3>Why it feels modern</h3>
            <ul>
              <li>Lighter blue navigation with crisp white and grey surfaces</li>
              <li>Modern sans-serif typography and softer panel edges</li>
              <li>Focused information hierarchy that keeps trading tools easy to scan</li>
            </ul>
          </div>
        </div>
      </section>
    </main>
  `,
})
export class HomePageComponent {}
