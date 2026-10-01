# Fix GitHub OAuth Redirect URI Error

## Problem
GitHub is rejecting the redirect URI with message:
> "The `redirect_uri` is not associated with this application. The application might be misconfigured or could be trying to redirect you to a website you weren't expecting."

## Root Cause
The redirect URI is not registered in your GitHub OAuth App settings.

## Solution: Update GitHub OAuth App Settings

### Step 1: Go to GitHub Settings
1. Open https://github.com/settings/developers
2. Click "OAuth Apps" in the left sidebar
3. Find and click your "Rocket Trading" application

### Step 2: Update Authorization Callback URL
1. Find the **"Authorization callback URL"** field
2. Clear any existing value
3. Enter this URL exactly:
   ```
   http://localhost:4200/auth/callback
   ```
   ⚠️ **Important:** Use exactly this URL with NO trailing slashes or query parameters

### Step 3: Save Changes
1. Scroll down and click **"Update application"**
2. You should see: "Application settings updated" ✅

### Step 4: Test Again
1. Go to http://localhost:4200
2. Click "Sign in with GitHub"
3. You should now be redirected to GitHub's authorization page (not get the error)

## Current Configuration in Your App

### Frontend (frontend/src/app/oauth.service.ts)
```typescript
const OAUTH_REDIRECT_URI = 'http://localhost:4200/auth/callback';
```

### Backend (backend/rocket-trading/src/main/java/com/rockettrading/rocket_trading/config/OAuthConfig.java)
```java
@Value("${oauth.redirect-uri:http://localhost:4200/auth/callback}")
private String redirectUri;

// Used as:
.redirectUri(redirectUri + "?provider=github")
```

## What the Authorization Flow Does

1. **User clicks "Sign in with GitHub"** on frontend
2. **Frontend redirects to** Spring Security endpoint: `/oauth2/authorization/github`
3. **Spring Security redirects to GitHub** with:
   - `client_id=Ov23lioG2Yix7Tsfr7U8`
   - `redirect_uri=http://localhost:4200/auth/callback?provider=github`
   - `scope=read:user,user:email`
4. **User authorizes on GitHub**
5. **GitHub redirects back to** `http://localhost:4200/auth/callback?code=...&state=...`
6. **Frontend's callback handler** exchanges the code for a token
7. **User is logged in!** ✅

## If You Still Get the Error

### Check 1: Verify the URL Exactly
Make sure in GitHub settings you entered:
```
http://localhost:4200/auth/callback
```
- ✅ Lowercase `http` (not HTTPS for localhost)
- ✅ Port `4200`
- ✅ Path `/auth/callback`
- ✅ NO query parameters
- ✅ NO trailing slash

### Check 2: Clear Browser Cache
1. Open DevTools (F12)
2. Go to Application → Cookies
3. Delete cookies for `localhost`
4. Close browser tab
5. Try again from http://localhost:4200

### Check 3: Verify Frontend is Running
```powershell
# Frontend should be running on port 4200
Get-NetTCPConnection -LocalPort 4200 -ErrorAction SilentlyContinue
```

### Check 4: Check Backend Environment Variables
```powershell
# Verify backend has correct GitHub credentials
$env:OAUTH_GITHUB_CLIENT_ID
$env:OAUTH_GITHUB_CLIENT_SECRET
```

## Reference: GitHub OAuth App Settings

Your OAuth app should have:
- **Client ID**: Ov23lioG2Yix7Tsfr7U8
- **Client Secret**: (should be hidden)
- **Authorization callback URL**: `http://localhost:4200/auth/callback`
- **Homepage URL**: `http://localhost:4200`
- **Application Name**: Rocket Trading (or similar)

All other fields are optional for local development.
