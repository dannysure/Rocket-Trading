# OAuth 2.0 Setup Guide for Rocket Trading

This guide explains how to set up OAuth 2.0 authentication for the Rocket Trading application, enabling secure sign-in via Google, GitHub, or Microsoft without handling passwords.

## Table of Contents
1. [Why OAuth?](#why-oauth)
2. [Supported Providers](#supported-providers)
3. [Setup Instructions](#setup-instructions)
4. [Testing](#testing)
5. [Production Deployment](#production-deployment)
6. [Troubleshooting](#troubleshooting)

## Why OAuth?

OAuth 2.0 provides several critical benefits:

✅ **No Password Storage**: Your app never stores or handles user passwords  
✅ **No Password Hashing**: Eliminates complexity of bcrypt, Argon2, etc.  
✅ **Reduced Security Risk**: Delegated to trusted providers (Google, GitHub, Microsoft)  
✅ **Automatic Account Creation**: New users sign up on first OAuth login  
✅ **Single Sign-On**: Users authenticate once across multiple apps  
✅ **Better Compliance**: Reduces GDPR/PCI/HIPAA concerns  
✅ **Lower Maintenance**: No password reset, account recovery complexity  

## Supported Providers

### 1. Google
- Most user-friendly OAuth provider
- 80%+ user coverage (most people have Gmail)
- Fast setup and approval process

### 2. GitHub
- Best for developer-focused applications
- 100% free
- Instant approval (no waiting for review)

### 3. Microsoft/Azure AD
- Enterprise-ready
- Integrates with Office 365, Teams
- Support for conditional access and MFA

## Setup Instructions

### Option A: Quick Start (GitHub - Fastest)

GitHub's OAuth is instant and requires no approval review.

#### Step 1: Create GitHub OAuth App

1. Go to https://github.com/settings/developers
2. Click "OAuth Apps" → "New OAuth App"
3. Fill in:
   - **Application name**: Rocket Trading (Dev)
   - **Homepage URL**: http://localhost:4200
   - **Authorization callback URL**: http://localhost:4200/auth/callback
4. Copy the **Client ID** and **Client Secret**

#### Step 2: Add to `.env`

```env
OAUTH_GITHUB_CLIENT_ID=your_client_id_here
OAUTH_GITHUB_CLIENT_SECRET=your_client_secret_here
```

#### Step 3: Start the Application

```bash
docker compose up -d
```

#### Step 4: Test Authentication

1. Open http://localhost:4200
2. Click "Sign in with GitHub"
3. Authorize the app
4. You should be logged in!

---

### Option B: Google OAuth Setup

#### Step 1: Create Google Cloud Project

1. Go to https://console.cloud.google.com/
2. Create a new project (name: "Rocket Trading")
3. Wait for creation to complete

#### Step 2: Enable OAuth 2.0 API

1. Click "Enable APIs and services"
2. Search for "Google+ API" and enable it
3. Go to "OAuth consent screen" → Configure external user type
4. Fill in:
   - **App name**: Rocket Trading
   - **User support email**: your-email@example.com
   - **Developer contact**: your-email@example.com
5. Click "Save and Continue"

#### Step 3: Create OAuth Credentials

1. Go to "Credentials" → "Create Credentials" → "OAuth client ID"
2. Choose "Web application"
3. Add Authorized redirect URIs:
   - `http://localhost:4200/auth/callback` (development)
   - `https://yourdomain.com/auth/callback` (production)
4. Click "Create"
5. Copy **Client ID** and **Client Secret**

#### Step 4: Add to `.env`

```env
OAUTH_GOOGLE_CLIENT_ID=your_client_id_here
OAUTH_GOOGLE_CLIENT_SECRET=your_client_secret_here
```

#### Step 5: Test

```bash
docker compose restart api
# Then open http://localhost:4200 and sign in with Google
```

---

### Option C: Microsoft OAuth Setup

#### Step 1: Create Azure App Registration

1. Go to https://portal.azure.com/
2. Go to "Azure Active Directory" → "App registrations"
3. Click "New registration"
4. Fill in:
   - **Name**: Rocket Trading
   - **Supported account types**: Personal and work accounts
   - **Redirect URI**: Web → `http://localhost:4200/auth/callback`
5. Click "Register"

#### Step 2: Create Client Secret

1. Go to "Certificates & secrets"
2. Click "New client secret"
3. Set expiration to 24 months
4. Copy the **Value** (this is your secret)

#### Step 3: Copy Application Credentials

1. Go to "Overview"
2. Copy **Application (client) ID** and **Tenant ID**

#### Step 4: Add to `.env`

```env
OAUTH_MICROSOFT_CLIENT_ID=your_application_id_here
OAUTH_MICROSOFT_CLIENT_SECRET=your_secret_here
```

#### Step 5: Test

```bash
docker compose restart api
```

---

## Configuration File Updates

### Backend Configuration (`application.properties`)

The backend automatically reads OAuth credentials from `.env`:

```properties
# Google
oauth.google.client-id=${OAUTH_GOOGLE_CLIENT_ID:}
oauth.google.client-secret=${OAUTH_GOOGLE_CLIENT_SECRET:}

# GitHub
oauth.github.client-id=${OAUTH_GITHUB_CLIENT_ID:}
oauth.github.client-secret=${OAUTH_GITHUB_CLIENT_SECRET:}

# Microsoft
oauth.microsoft.client-id=${OAUTH_MICROSOFT_CLIENT_ID:}
oauth.microsoft.client-secret=${OAUTH_MICROSOFT_CLIENT_SECRET:}

# Redirect
oauth.redirect-uri=${OAUTH_REDIRECT_URI:http://localhost:4200/auth/callback}
```

### Frontend Configuration

The frontend automatically uses configured providers from the backend:

```typescript
// frontend/src/app/oauth.service.ts
initiateOAuthLogin(provider: string): void {
  // Redirects to backend OAuth endpoint
  window.location.href = `${API_BASE_URL}/auth/oauth2/authorization/${provider}`;
}
```

## Authentication Flow

```
User Opens App
    ↓
[Login Component] Shows provider buttons (Google, GitHub, Microsoft)
    ↓
User clicks "Sign in with GitHub"
    ↓
Frontend redirects to backend: /api/v1/auth/oauth2/authorization/github
    ↓
Backend redirects to GitHub: https://github.com/login/oauth/authorize
    ↓
User logs in to GitHub and authorizes app
    ↓
GitHub redirects to: /auth/callback?code=xxx&state=xxx
    ↓
Backend exchanges code for token
    ↓
Backend creates/loads user from database
    ↓
Backend generates application JWT token
    ↓
Backend redirects to: http://localhost:4200/auth/callback?token=xxx
    ↓
Frontend stores token in sessionStorage
    ↓
Frontend redirects to /dashboard
    ↓
User is logged in! ✅
```

## Testing

### Manual Testing

1. **Register new user**:
   ```bash
   open http://localhost:4200
   # Click GitHub sign-in
   # Authorize the app
   # Should see dashboard
   ```

2. **Verify user created in database**:
   ```bash
   psql -h localhost -p 5435 -U team_rocket_admin -d team_rocket_db
   SELECT * FROM client_profiles;
   ```

3. **Test token expiration**:
   ```bash
   # Check JWT_EXPIRATION_HOURS in .env (default 8 hours)
   # Wait for expiration and see if app handles it gracefully
   ```

### Automated Testing

Add to your test suite:

```python
# tests/test_oauth.py
import requests

def test_oauth_callback():
    """Test OAuth callback endpoint"""
    response = requests.get(
        'http://localhost:8081/api/v1/auth/oauth/success',
        headers={'Authorization': 'Bearer test-token'}
    )
    assert response.status_code == 200

def test_user_creation():
    """Test automatic user creation on first OAuth login"""
    # Simulate OAuth flow and verify user is created
    pass
```

## Production Deployment

### Security Checklist

Before deploying to production:

⚠️ **Environment Variables**:
- [ ] Set strong random values for JWT_SECRET
- [ ] Configure production OAuth callback URLs
- [ ] Use HTTPS (not HTTP) for all OAuth redirects
- [ ] Store secrets in secrets manager (AWS Secrets Manager, Vault, etc.)

⚠️ **CORS Configuration**:
```java
// In OAuthConfig.java
configuration.setAllowedOrigins(Arrays.asList(
  "https://yourdomain.com",  // Production domain only
  "https://app.yourdomain.com"
));
```

⚠️ **Session Management**:
```java
// Use persistent session store (Redis) instead of SimpleBroker
@Bean
public SessionRepository sessionRepository() {
  return new RedisIndexedSessionRepository(connectionFactory);
}
```

⚠️ **Token Refresh**:
```typescript
// Add automatic token refresh logic
private refreshTokenIfNeeded() {
  const session = this.readSession();
  const expiresAt = new Date(session.expiresAt);
  const now = new Date();
  
  if (expiresAt - now < 5 * 60 * 1000) { // Less than 5 minutes
    this.refreshToken().subscribe();
  }
}
```

### Production `.env` Example

```env
# Database (Use managed service like RDS)
DB_NAME=rocket_trading_prod
DB_USERNAME=prod_admin
DB_PASSWORD=<generate-strong-password>
DB_PORT=5432

# JWT (Change from development secret!)
JWT_SECRET=<generate-random-string-min-32-chars>
JWT_ISSUER=rocket-trading
JWT_EXPIRATION_HOURS=8

# Kafka (Use managed service like MSK or Confluent Cloud)
KAFKA_BOOTSTRAP_SERVERS=kafka-broker-1:9092,kafka-broker-2:9092

# API Configuration
SERVER_PORT=8081
API_BASE_URL=https://api.yourdomain.com/api/v1
FRONTEND_URL=https://app.yourdomain.com

# OAuth Credentials (Get from provider dashboards)
OAUTH_GOOGLE_CLIENT_ID=<production-google-client-id>
OAUTH_GOOGLE_CLIENT_SECRET=<production-google-secret>
OAUTH_GITHUB_CLIENT_ID=<production-github-client-id>
OAUTH_GITHUB_CLIENT_SECRET=<production-github-secret>
OAUTH_REDIRECT_URI=https://app.yourdomain.com/auth/callback
```

## Troubleshooting

### Issue: "Redirect URI mismatch"

**Problem**: OAuth provider rejects redirect

**Solution**:
1. Verify `OAUTH_REDIRECT_URI` matches what's configured in OAuth provider
2. Check that callback URL is HTTPS in production (not HTTP)
3. Ensure trailing slash consistency

### Issue: "User not found after OAuth"

**Problem**: Token works but user not in database

**Solution**:
1. Check that `OAuthUserService` is being called
2. Verify `ClientProfileRepository.findByEmail()` is working
3. Check database connection:
   ```bash
   psql -h localhost -p 5435 -U team_rocket_admin -d team_rocket_db -c "SELECT 1"
   ```

### Issue: "CORS error in browser console"

**Problem**: Browser blocks OAuth redirects

**Solution**:
1. Update CORS configuration in `OAuthConfig.java`
2. Add frontend origin to allowed origins:
   ```java
   configuration.setAllowedOrigins(Arrays.asList(
     "http://localhost:4200",
     "https://yourdomain.com"
   ));
   ```

### Issue: "Session expires immediately"

**Problem**: User logged out after page refresh

**Solution**:
1. Check `JWT_EXPIRATION_HOURS` setting
2. Verify token is stored in `sessionStorage` (not `localStorage`)
3. Check browser privacy settings aren't blocking storage

## Next Steps

After implementing OAuth:

1. **Remove Legacy Auth**: Delete local password-based auth code
   ```bash
   rm backend/rocket-trading/src/main/java/com/rockettrading/rocket_trading/controller/AuthController.java
   rm frontend/src/app/register.component.ts
   rm frontend/src/app/sign-in.component.ts
   ```

2. **Add Account Linking**: Allow users to link multiple providers

3. **Add MFA**: Integrate multi-factor authentication

4. **Analytics**: Track OAuth provider usage for insights

5. **Rate Limiting**: Prevent OAuth abuse:
   ```java
   @RateLimiter(10)  // 10 requests per minute
   @PostMapping("/auth/oauth/success")
   public Map<String, Object> oauthSuccess() { ... }
   ```

## References

- [Google OAuth Docs](https://developers.google.com/identity/protocols/oauth2)
- [GitHub OAuth Docs](https://docs.github.com/en/developers/apps/building-oauth-apps)
- [Microsoft Identity Platform](https://learn.microsoft.com/en-us/entra/identity-platform/)
- [Spring Security OAuth2](https://spring.io/projects/spring-security-oauth)
- [OpenID Connect Spec](https://openid.net/specs/openid-connect-core-1_0.html)

## Support

For issues or questions:
1. Check the [Troubleshooting](#troubleshooting) section above
2. Review logs: `docker compose logs api`
3. Check network tab in browser DevTools
4. See [SETUP_AND_TESTING.md](SETUP_AND_TESTING.md) for general app setup
