import { Routes } from '@angular/router';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { OAuthService } from './oauth.service';
import { AuthService } from './api.service';
import { LoginComponent } from './login.component';
import { CallbackComponent } from './callback.component';
import { CompleteProfileComponent } from './complete-profile.component';
import { RegisterComponent } from './register.component';
import { SignInComponent } from './sign-in.component';
import { DashboardComponent } from './dashboard.component';

/**
 * Guard to protect routes that require authentication
 * Checks if user has valid OAuth session
 */
export const authGuard = (route: any, state: any) => {
  const oauthService = inject(OAuthService);
  const router = inject(Router);

  if (oauthService.isAuthenticated()) {
    return true;
  }

  // Fallback to legacy AuthService for backward compatibility
  const authService = inject(AuthService);
  if (authService.isSignedIn()) {
    return true;
  }

  router.navigate(['/login']);
  return false;
};

/**
 * Guard to redirect authenticated users away from auth pages
 */
export const noAuthGuard = (route: any, state: any) => {
  const oauthService = inject(OAuthService);
  const authService = inject(AuthService);
  const router = inject(Router);

  const isOAuthAuthenticated = oauthService.isAuthenticated();
  const isLegacyAuthenticated = authService.isSignedIn();

  if (!isOAuthAuthenticated && !isLegacyAuthenticated) {
    return true;
  }

  router.navigate(['/dashboard']);
  return false;
};

export const routes: Routes = [
  // Default redirect
  { path: '', redirectTo: '/dashboard', pathMatch: 'full' },

  // OAuth authentication flow
  { path: 'login', component: LoginComponent, canActivate: [noAuthGuard] },
  { path: 'auth/callback', component: CallbackComponent },
  { path: 'complete-profile', component: CompleteProfileComponent, canActivate: [authGuard] },

  // Legacy authentication flow (deprecated, kept for backward compatibility)
  { path: 'register', component: RegisterComponent, canActivate: [noAuthGuard] },
  { path: 'sign-in', component: SignInComponent, canActivate: [noAuthGuard] },

  // Protected routes
  { path: 'dashboard', component: DashboardComponent, canActivate: [authGuard] },

  // Catch-all
  { path: '**', redirectTo: '/dashboard' },
];
