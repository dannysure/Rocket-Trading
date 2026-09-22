import { Routes } from '@angular/router';
import { ChartsPageComponent } from './charts-page.component';
import { HomePageComponent } from './home-page.component';
import { NewsPageComponent } from './news-page.component';
import { PerpetualFuturesPageComponent } from './perpetual-futures-page.component';
import { PortfolioPageComponent } from './portfolio-page.component';

export const routes: Routes = [
  { path: '', component: HomePageComponent },
  { path: 'portfolio', component: PortfolioPageComponent },
  { path: 'news', component: NewsPageComponent },
  { path: 'charts', component: ChartsPageComponent },
  { path: 'perpetual-futures', component: PerpetualFuturesPageComponent },
  { path: '**', redirectTo: '' },
];
