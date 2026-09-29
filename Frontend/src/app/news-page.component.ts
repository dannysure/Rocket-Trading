import { Component } from '@angular/core';

@Component({
  selector: 'app-news-page',
  standalone: true,
  template: `
    <main class="page subpage">
      <p class="section-label">Realtime coverage</p>
      <h1>News</h1>
      <p class="lead">
        Follow curated headlines, earnings updates, and macro developments in a clean,
        modern news workspace.
      </p>
    </main>
  `,
})
export class NewsPageComponent {}
