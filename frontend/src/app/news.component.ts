import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { NewsApi, NewsArticle, errorMessage } from './api.service';
import { NavHeaderComponent } from './nav-header.component';

@Component({
  selector: 'app-news',
  imports: [DatePipe, NavHeaderComponent],
  template: `
    <app-nav-header />
    <main class="page news-page">
      <div class="page-heading">
        <div>
          <p class="eyebrow">Market intelligence</p>
          <h1 class="page-title">Latest news</h1>
          <p class="page-subtitle">The stories moving markets, gathered in one place.</p>
        </div>
        <button type="button" class="btn btn-outline refresh" (click)="load()">Refresh</button>
      </div>

      @if (error()) { <p class="alert error" role="alert">{{ error() }}</p> }
      @if (loading()) {
        <section class="news-list" aria-label="Loading market news">
          <p class="skeleton">Loading market news…</p>
        </section>
      } @else if (articles().length) {
        <section class="news-list" aria-label="Latest market news">
          @for (article of articles(); track article.id) {
            <a class="news-item" [href]="article.url" target="_blank" rel="noopener noreferrer">
              @if (article.imageUrl) { <img [src]="article.imageUrl" alt="" loading="lazy" /> }
              <div class="news-copy">
                <div class="news-meta"><span>{{ article.source }}</span><time>{{ article.publishedAt | date: 'MMM d, y · HH:mm' }}</time></div>
                <h2>{{ article.headline }}</h2>
                @if (article.summary) { <p>{{ article.summary }}</p> }
              </div>
              <span class="arrow" aria-hidden="true">↗</span>
            </a>
          }
        </section>
      } @else {
        <section class="card empty-news">
          <h2>News is not connected yet</h2>
          <p>Set <code>NEWS_API_KEY</code> in the backend environment to show the latest market stories.</p>
        </section>
      }
    </main>
  `,
  styles: [`
    .news-page { max-width: 960px; }
    .page-heading { display: flex; align-items: flex-end; justify-content: space-between; gap: 20px; margin-bottom: 24px; }
    .eyebrow { color: var(--brand); font-size: 12px; font-weight: 700; letter-spacing: .06em; margin: 0 0 6px; text-transform: uppercase; }
    .page-title { margin-bottom: 6px; }
    .page-subtitle { margin-bottom: 0; }
    .refresh { min-height: 38px; padding: 8px 14px; }
    .news-list { display: flex; flex-direction: column; gap: 12px; }
    .news-item { display: grid; grid-template-columns: 148px minmax(0, 1fr) auto; gap: 20px; align-items: center; padding: 16px; background: var(--surface); border: 1px solid var(--border); border-radius: 12px; transition: border-color 140ms ease, transform 140ms ease; }
    .news-item:hover { border-color: var(--brand); transform: translateY(-1px); }
    .news-item img { width: 148px; height: 98px; object-fit: cover; border-radius: 8px; background: var(--surface-alt); }
    .news-copy { min-width: 0; }
    .news-meta { display: flex; gap: 10px; color: var(--text-muted); font-size: 12px; margin-bottom: 7px; }
    .news-meta span { color: var(--brand); font-weight: 700; }
    .news-copy h2 { font-size: 17px; line-height: 1.3; margin: 0; }
    .news-copy p { color: var(--text-muted); font-size: 13px; line-height: 1.45; margin: 8px 0 0; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
    .arrow { color: var(--brand); font-size: 20px; }
    .empty-news { text-align: center; padding: 48px 24px; }
    .empty-news h2 { font-size: 20px; }
    .empty-news p { color: var(--text-muted); font-size: 14px; }
    @media (max-width: 640px) { .page-heading { align-items: flex-start; } .news-item { grid-template-columns: 1fr auto; } .news-item img { width: 100%; height: 150px; grid-column: 1 / -1; } }
  `],
})
export class NewsComponent implements OnInit {
  private readonly newsApi = inject(NewsApi);
  protected readonly articles = signal<NewsArticle[]>([]);
  protected readonly loading = signal(false);
  protected readonly error = signal('');

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading.set(true);
    this.error.set('');
    this.newsApi.market().subscribe({
      next: (articles) => { this.articles.set(articles ?? []); this.loading.set(false); },
      error: (error) => { this.error.set(errorMessage(error, 'Market news is unavailable right now.')); this.loading.set(false); },
    });
  }
}
