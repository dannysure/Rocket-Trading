# Rocket Trading - Quick Start (5 minutes)

## Prerequisites
- Node.js (npm)
- Java 21
- PostgreSQL 16+
- GitHub account for OAuth

## Quick Setup

### 1. Configure GitHub OAuth (2 min)
1. Go to https://github.com/settings/developers → OAuth Apps → New OAuth App
2. Set **Authorization callback URL** to: `http://localhost:4200/auth/callback`
3. Copy your Client ID and Client Secret

### 2. Setup Environment (1 min)
```powershell
copy .env.example .env
# Edit .env and add your GitHub OAuth credentials:
# OAUTH_GITHUB_CLIENT_ID=your_id
# OAUTH_GITHUB_CLIENT_SECRET=your_secret
```

### 3. Setup Database (1 min)
```powershell
psql -U postgres

# In psql:
CREATE USER team_rocket_admin WITH PASSWORD 'team_rocket_password123';
CREATE DATABASE team_rocket_db OWNER team_rocket_admin;
GRANT ALL PRIVILEGES ON DATABASE team_rocket_db TO team_rocket_admin;
\q
```

### 4. Run Database Migrations
```powershell
cd backend\rocket-trading
./mvnw clean flyway:migrate -Dspring.datasource.url=jdbc:postgresql://localhost:5432/team_rocket_db -Dspring.datasource.username=team_rocket_admin -Dspring.datasource.password=team_rocket_password123
```

### 5. Start Services (Open 3 terminals)

**Terminal 1 - Backend**:
```powershell
cd backend\rocket-trading
./mvnw clean spring-boot:run
```
✅ Wait for "Server listening on port 8081"

**Terminal 2 - Frontend**:
```powershell
cd frontend
npm install  # First time only
npm start
```
✅ Wait for "Local: http://localhost:4200"

**Terminal 3 - Database** (keep PostgreSQL running):
```powershell
# PostgreSQL should be running. If not:
net start postgresql-x64-16
```

### 6. Access the App
Open **http://localhost:4200** in your browser and click "Sign in with GitHub"

---

## Architecture

```
Frontend (Angular)          Backend (Spring Boot)        Database (PostgreSQL)
http://localhost:4200    http://localhost:8081/api/v1   localhost:5432
        ↓                        ↓                             ↓
   OAuth Login    ←→    GitHub OAuth Handler    ←→    User & Account Data
   Dashboard             Trading API                    Trading Data
   Portfolio             Order Matching                 Portfolio History
```

---

## OAuth Flow

```
1. User clicks "Sign in with GitHub" on http://localhost:4200
2. Browser redirects to: https://github.com/login/oauth/authorize
3. User authorizes Rocket Trading app
4. GitHub redirects back to: http://localhost:4200/auth/callback
5. Frontend exchanges code for JWT token
6. Frontend stores token and shows dashboard
```

---

## Ports

| Service | Port | URL |
|---------|------|-----|
| Frontend (Angular) | 4200 | http://localhost:4200 |
| Backend API | 8081 | http://localhost:8081/api/v1 |
| Database | 5432 | localhost:5432 |

---

## Verify Everything Works

```powershell
# Backend health check
curl http://localhost:8081/api/v1/health

# Frontend check
curl http://localhost:4200
```

---

## Troubleshooting

| Issue | Solution |
|-------|----------|
| "Port already in use" | Run: `netstat -ano \| findstr :8081` to find process ID, then `taskkill /PID <id> /F` |
| "Database connection refused" | Ensure PostgreSQL is running: `net start postgresql-x64-16` |
| "GitHub OAuth failed" | Check `.env` has correct Client ID/Secret from https://github.com/settings/developers |
| "CORS error" | Backend CORS is configured for `http://localhost:4200` |

---

## Production Notes

⚠️ **This setup is for LOCAL DEVELOPMENT ONLY**

For production:
- Change `JWT_SECRET` to a strong random value
- Use HTTPS everywhere
- Secure all passwords in a secrets manager
- Update GitHub OAuth to use your production domain
- Deploy with Docker/Kubernetes

---

## Next Steps

1. Read [LOCAL_SETUP.md](LOCAL_SETUP.md) for detailed instructions
2. Check [docs/API_DOCUMENTATION.md](docs/API_DOCUMENTATION.md) for API details
3. Review [backend/rocket-trading/README.md](backend/rocket-trading/README.md) for backend info
4. Check [frontend/README.md](frontend/README.md) for frontend info

---

## Support

- All configuration is in `.env` file (keep it private!)
- Database schema migrations in `database/` folder
- API endpoints documented with Swagger (when available)
- Frontend source in `frontend/src/`
- Backend source in `backend/rocket-trading/src/`
