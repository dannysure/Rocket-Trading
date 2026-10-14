import { RenderMode, ServerRoute } from '@angular/ssr';

// Every screen depends on the signed-in session held in the browser, so nothing is prerendered
export const serverRoutes: ServerRoute[] = [
  {
    path: '**',
    renderMode: RenderMode.Client,
  },
];
