# GitHub OAuth Setup for Rocket Trading

Rocket Trading supports signing in with GitHub. The backend refuses to start without GitHub
OAuth credentials, so every developer needs a GitHub OAuth app.

## Setup

### Step 1: Create a GitHub OAuth App

1. Go to https://github.com/settings/developers
2. Click "OAuth Apps" → "New OAuth App"
3. Fill in:
   - **Application name**: Rocket Trading (Dev)
   - **Homepage URL**: http://localhost:4200
   - **Authorization callback URL**: the same value as `OAUTH_REDIRECT_URI` in `.env`
4. Copy the **Client ID** and **Client Secret**

### Step 2: Add to `.env`

```env
OAUTH_GITHUB_CLIENT_ID=your_client_id_here
OAUTH_GITHUB_CLIENT_SECRET=your_client_secret_here
OAUTH_REDIRECT_URI=http://localhost:4200/auth/callback
```

The backend reads these through `application.properties`:

```properties
oauth.redirect-uri=${OAUTH_REDIRECT_URI:http://localhost:4200/auth/callback}
oauth.github.client-id=${OAUTH_GITHUB_CLIENT_ID:}
oauth.github.client-secret=${OAUTH_GITHUB_CLIENT_SECRET:}
```

### Step 3: Start and Test

```bash
docker compose up -d
```

1. Open http://localhost:4200
2. Click "Sign in with GitHub" and authorize the app
3. You land on the dashboard. A new client gets a DIRECT_TRADING account automatically.

## Authentication Flow

```
Login page: "Sign in with GitHub"
    ↓
Frontend → /api/v1/oauth2/authorization/github (OAuthAuthorizationController)
    ↓
Backend → /oauth2/authorization/github (Spring Security) → GitHub
    ↓
User authorizes; GitHub returns the authorization code
    ↓
OAuthUserService loads or creates the client and its trading account
    ↓
OAuth2AuthenticationSuccessHandler creates a session and an application JWT,
then redirects to http://localhost:4200/auth/callback?token=...
    ↓
Frontend stores the session in sessionStorage and opens the dashboard
```

Verify the client was created:

```bash
psql -h localhost -p 5435 -U team_rocket_admin -d team_rocket_db -c "SELECT * FROM client_profiles;"
```

## Production Checklist

- Set a strong random `JWT_SECRET` (at least 32 characters)
- Register production callback URLs in the GitHub app and use HTTPS for all redirects
- Restrict CORS origins in `SecurityConfig.java` to the production domain
- Keep secrets in a secrets manager rather than `.env`

## Troubleshooting

**"Redirect URI mismatch"**: `OAUTH_REDIRECT_URI` must exactly match the callback URL in the
GitHub app, including scheme, port and trailing slash.

**Backend fails at startup with "GitHub OAuth credentials are required"**: set
`OAUTH_GITHUB_CLIENT_ID` and `OAUTH_GITHUB_CLIENT_SECRET` in `.env`.

**User not found after OAuth**: check `docker compose logs api` for `OAuthUserService`
errors, and confirm the database is reachable.

**Session expires immediately**: check `JWT_EXPIRATION_HOURS` and that the browser is not
blocking `sessionStorage`.

## References

- [GitHub OAuth Apps](https://docs.github.com/en/apps/oauth-apps/building-oauth-apps)
- [Spring Security OAuth2 Login](https://docs.spring.io/spring-security/reference/servlet/oauth2/login/index.html)
