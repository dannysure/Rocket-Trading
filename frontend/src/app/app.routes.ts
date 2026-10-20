import { inject } from '@angular/core';
import { CanActivateFn, Router, Routes } from '@angular/router';
import { Role, SessionStore } from './api.service';
import { LoginComponent } from './login.component';
import { AdminLoginComponent } from './admin-login.component';
import { CallbackComponent } from './callback.component';
import { CompleteProfileComponent } from './complete-profile.component';
import { DashboardComponent } from './dashboard.component';
import { TradeComponent } from './trade.component';
import { BlotterComponent } from './blotter.component';
import { PerpsComponent } from './perps.component';
import { InsightsComponent } from './insights.component';
import { OpsComponent } from './ops.component';
import { NewsComponent } from './news.component';

/** Lets a signed-in person with one of the roles through; anyone else goes to sign-in or their own home. */
export function requireRole(...roles: Role[]): CanActivateFn {
  return () => {
    const sessions = inject(SessionStore);
    const router = inject(Router);
    if (!sessions.isActive()) {
      sessions.clear();
      return router.createUrlTree(['/login']);
    }
    const role = sessions.role();
    return role && roles.includes(role) ? true : router.createUrlTree([sessions.homeRoute()]);
  };
}

export const signedOutOnly: CanActivateFn = () => {
  const sessions = inject(SessionStore);
  return sessions.isActive() ? inject(Router).createUrlTree([sessions.homeRoute()]) : true;
};

export const routeToHome: CanActivateFn = () => {
  const sessions = inject(SessionStore);
  return inject(Router).createUrlTree([sessions.isActive() ? sessions.homeRoute() : '/login']);
};

export const routes: Routes = [
  { path: '', pathMatch: 'full', canActivate: [routeToHome], children: [] },
  { path: 'login', component: LoginComponent, canActivate: [signedOutOnly], title: 'Sign in · Rocket Trading' },
  { path: 'admin/login', component: AdminLoginComponent, canActivate: [signedOutOnly], title: 'Admin sign in · Rocket Trading' },
  { path: 'auth/callback', component: CallbackComponent, title: 'Signing in · Rocket Trading' },
  { path: 'complete-profile', component: CompleteProfileComponent, canActivate: [requireRole('CLIENT')], title: 'Your profile · Rocket Trading' },

  { path: 'dashboard', component: DashboardComponent, canActivate: [requireRole('CLIENT')], title: 'Dashboard · Rocket Trading' },
  { path: 'trade', component: TradeComponent, canActivate: [requireRole('CLIENT')], title: 'Markets · Rocket Trading' },
  { path: 'news', component: NewsComponent, canActivate: [requireRole('CLIENT')], title: 'News · Rocket Trading' },
  { path: 'blotter', component: BlotterComponent, canActivate: [requireRole('CLIENT')], title: 'Blotter · Rocket Trading' },
  { path: 'perps', component: PerpsComponent, canActivate: [requireRole('CLIENT')], title: 'Perpetuals · Rocket Trading' },

  { path: 'insights', component: InsightsComponent, canActivate: [requireRole('ANALYST', 'OPERATIONS')], title: 'Insights · Rocket Trading' },
  { path: 'ops', component: OpsComponent, canActivate: [requireRole('OPERATIONS')], title: 'Trade lookup · Rocket Trading' },

  // Old sign-in pages now live on /login
  { path: 'register', redirectTo: 'login' },
  { path: 'sign-in', redirectTo: 'login' },
  { path: '**', canActivate: [routeToHome], children: [] },
];
