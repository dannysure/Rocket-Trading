# Project Cleanup & OAuth Migration Plan

## Phase 1: Cleanup ✅

### Files to DELETE (Unnecessary/Duplicate)

**Old/Duplicate Services**:
- `frontend/src/app/trading-api.service.ts` - REMOVE (replaced by api.service.ts)
- `frontend/src/app/market-data.service.ts` - REMOVE (unused, no imports)

**Old Page Components** (will be replaced by dashboard):
- `frontend/src/app/home-page.component.ts` - REMOVE
- `frontend/src/app/portfolio-page.component.ts` - REMOVE
- `frontend/src/app/charts-page.component.ts` - REMOVE
- `frontend/src/app/news-page.component.ts` - REMOVE
- `frontend/src/app/perpetual-futures-page.component.ts` - REMOVE
- `frontend/src/app/tradingview-chart.component.ts` - REMOVE

**Obsolete Configuration**:
- `.env.example` - REMOVE (use .env template for documentation instead)

**Build/CI Files** (keep if actively using, otherwise remove):
- `Jenkinsfile` - OPTIONAL REMOVE (unless using Jenkins CI/CD)
- `compose.e2e.yaml` - OPTIONAL REMOVE (unless running E2E tests)
- `start.sh` - REMOVE (Docker Compose is primary method)

**Unnecessary Python/Config**:
- `pytest.ini` - REMOVE (no Python unit tests in project)
- `__pycache__/` - REMOVE (Python cache)

**Analytics** (separate concern):
- `analytics/analytics.ipynb` - KEEP (separate analytics workbook)
- `api/` (Python analytics API) - KEEP but document as optional

### Files to CONSOLIDATE

**Environment Variables**:
- `.env` - KEEP and enhance
- `.env.example` - REMOVE
- Create `docs/ENV_SETUP.md` for documentation

## Phase 2: OAuth Setup 🔐

### Backend Changes (Spring Boot)

**New Configuration Files**:
1. `OAuthConfig.java` - OAuth2 configuration
2. `OAuthUserService.java` - Load user from OAuth provider
3. `JwtTokenProvider.java` - Generate JWT from OAuth token

**Controller Changes**:
- Replace `AuthController` with OAuth endpoints
- Add `/api/v1/auth/oauth/callback` endpoint
- Add `/api/v1/auth/oauth/login` endpoint
- Add `/api/v1/auth/me` endpoint (get current user)
- Keep `/api/v1/auth/sign-out` for logout

**Dependencies**:
- `spring-boot-starter-oauth2-client` (OAuth2 client)
- `spring-boot-starter-oauth2-resource-server` (validate tokens)

### Frontend Changes (Angular)

**New Services**:
- `OAuthService` - Handle OAuth flow, token exchange

**New Components**:
- `LoginComponent` - OAuth provider selection
- `CallbackComponent` - Handle OAuth redirect

**Removed Components**:
- Remove `RegisterComponent` (OAuth handles signup)
- Update `SignInComponent` to use OAuth providers

**Routes**:
```
/login                    → LoginComponent (OAuth provider selection)
/auth/callback            → CallbackComponent (OAuth redirect handler)
/dashboard                → DashboardComponent (protected)
```

### Environment Variables (OAuth)

```env
# OAuth Providers Configuration
OAUTH_GOOGLE_CLIENT_ID=xxx
OAUTH_GOOGLE_CLIENT_SECRET=xxx
OAUTH_GITHUB_CLIENT_ID=xxx
OAUTH_GITHUB_CLIENT_SECRET=xxx

# Frontend OAuth Redirect
OAUTH_REDIRECT_URI=http://localhost:4200/auth/callback

# Backend OAuth
OAUTH_SCOPE=openid profile email
```

## Benefits of OAuth Setup

✅ No password storage in database
✅ No password hashing complexity
✅ Single sign-on across providers (Google, GitHub, Microsoft, etc.)
✅ Better security (delegated to established providers)
✅ Users manage their credentials at provider (not your app)
✅ Easier compliance (GDPR, security regulations)
✅ Reduced liability (don't store sensitive credentials)

## Migration Path

1. **Day 1**: Clean up project structure
2. **Day 2-3**: Implement OAuth in backend
3. **Day 3-4**: Implement OAuth in frontend
4. **Day 4**: Remove local auth code
5. **Day 5**: Test end-to-end flow
