# Google OAuth Setup for Rocket Trading

Clients sign in with Google. Administrators and operations users sign in with username or email and password at `/admin/login`.

## Google setup

1. Create a Google OAuth web application.
2. Add this authorized redirect URI:

```text
http://localhost:8081/login/oauth2/code/google
```

3. Set the credentials in `.env`:

```env
OAUTH_GOOGLE_CLIENT_ID=your-client-id.apps.googleusercontent.com
OAUTH_GOOGLE_CLIENT_SECRET=your-client-secret
OAUTH_GOOGLE_REDIRECT_URI=http://localhost:8081/login/oauth2/code/google
```

4. Rebuild the API and UI:

```bash
docker-compose up -d --build api ui
```

## Authentication flow

```text
Client clicks Continue with Google
    -> /api/v1/oauth2/authorization/google
    -> Google authorization
    -> /auth/callback?token=...
    -> client session and dashboard
```

Admin users go directly to `/admin/login` and use their provisioned credentials.
Keep OAuth secrets in `.env` or a secrets manager; do not commit them.
