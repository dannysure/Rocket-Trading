# Rocket Trading - Local Development Setup (Without Docker)

This guide explains how to run the **Rocket Trading** application locally on your machine without Docker. The application is a full-stack trading platform with:
- **Frontend**: Angular on port 4200
- **Backend**: Spring Boot Java API on port 8081  
- **Database**: PostgreSQL on port 5432
- **Authentication**: GitHub OAuth 2.0

---

## Prerequisites

Make sure you have these tools installed:

### Windows
1. **Node.js & npm** (for Angular frontend)
   - Download: https://nodejs.org/ (LTS version recommended)
   - Verify: `node --version` and `npm --version`

2. **Java 21** (for Spring Boot backend)
   - Download: https://www.oracle.com/java/technologies/downloads/
   - Verify: `java --version`
   - Verify Maven: `mvn --version` (Maven should be bundled with the Spring Boot project)

3. **PostgreSQL 16+** (for database)
   - Download: https://www.postgresql.org/download/windows/
   - During installation, remember the password you set for the `postgres` user
   - Verify: `psql --version`

4. **Git** (for version control)
   - Download: https://git-scm.com/download/win

---

## Step 1: GitHub OAuth Configuration (Required)

### 1a: Create GitHub OAuth Application

1. Go to https://github.com/settings/developers
2. Click "OAuth Apps" → "New OAuth App"
3. Fill in the form:
   - **Application name**: `Rocket Trading (Local)`
   - **Homepage URL**: `http://localhost:4200`
   - **Authorization callback URL**: `http://localhost:4200/auth/callback`
4. Click "Register application"
5. Copy your **Client ID** and **Client Secret** (keep secret safe!)

### 1b: Configure Environment Variables

1. Open the project root directory in a terminal
2. Copy `.env.example` to `.env`:
   ```bash
   copy .env.example .env
   ```
3. Edit `.env` and fill in:
   ```env
   OAUTH_GITHUB_CLIENT_ID=<your_client_id_from_step_1a>
   OAUTH_GITHUB_CLIENT_SECRET=<your_client_secret_from_step_1a>
   OAUTH_REDIRECT_URI=http://localhost:4200/auth/callback
   ```

**IMPORTANT**: Never commit `.env` to version control!

---

## Step 2: Database Setup

### 2a: Create PostgreSQL Database

Open PowerShell and run:

```powershell
# Connect to PostgreSQL as admin
psql -U postgres

# In the psql prompt, run:
CREATE USER team_rocket_admin WITH PASSWORD 'team_rocket_password123';
CREATE DATABASE team_rocket_db OWNER team_rocket_admin;

# Give privileges
GRANT ALL PRIVILEGES ON DATABASE team_rocket_db TO team_rocket_admin;

# Exit psql
\q
```

### 2b: Run Database Migrations

```powershell
cd backend\rocket-trading

# Run with Maven
./mvnw clean flyway:migrate -Dspring.datasource.url=jdbc:postgresql://localhost:5432/team_rocket_db -Dspring.datasource.username=team_rocket_admin -Dspring.datasource.password=team_rocket_password123
```

This will automatically create all necessary tables from the migration scripts.

---

## Step 3: Start the Backend (Spring Boot API)

### 3a: Build and Start

```powershell
cd backend\rocket-trading

# Option 1: Using Maven (Clean build)
./mvnw clean spring-boot:run

# Option 2: If you already built it
java -jar target/rocket-trading-0.0.1-SNAPSHOT.jar
```

**Expected output**:
```
Started RocketTradingApplication in X.XXX seconds
Server listening on port 8081
```

The API is now available at: **http://localhost:8081/api/v1**

### 3b: Verify Backend is Running

Open a new terminal:
```powershell
# Test health endpoint
curl http://localhost:8081/api/v1/health

# Should return a 200 OK response
```

---

## Step 4: Start the Frontend (Angular)

### 4a: Install Dependencies

```powershell
cd frontend

# Install npm packages
npm install
```

### 4b: Start Development Server

```powershell
# Start Angular development server
npm start

# Or use the ng CLI directly
ng serve
```

**Expected output**:
```
✔ Compiled successfully.
✔ Build cache restored.
Local: http://localhost:4200/
```

The frontend is now available at: **http://localhost:4200**

---

## Step 5: Verify Everything is Working

### 5a: Test Application

1. Open **http://localhost:4200** in your browser
2. You should see the login page
3. Click "Sign in with GitHub"
4. You'll be redirected to GitHub to authorize
5. After authorization, you'll be logged in to the app
6. You should see the dashboard

### 5b: API Testing (Optional)

Use Postman or curl to test endpoints:

```powershell
# Get current user (requires JWT token from login)
curl -H "Authorization: Bearer YOUR_JWT_TOKEN" http://localhost:8081/api/v1/auth/me

# Get platform overview
curl http://localhost:8081/api/v1/platform/overview
```

---

## Step 6: Running Multiple Services

When developing, you'll need 3 separate terminal windows:

### Terminal 1: Backend (Spring Boot)
```powershell
cd backend\rocket-trading
./mvnw clean spring-boot:run
```

### Terminal 2: Frontend (Angular)
```powershell
cd frontend
npm start
```

### Terminal 3: Database (PostgreSQL - keep running)
PostgreSQL should be running as a service. If not, start it:
```powershell
# Start PostgreSQL service (if not running)
net start postgresql-x64-16

# Or connect directly if already running
psql -U team_rocket_admin -d team_rocket_db
```

---

## Environment Variables Reference

All configuration is read from `.env` file:

| Variable | Default | Description |
|----------|---------|-------------|
| `OAUTH_GITHUB_CLIENT_ID` | - | **REQUIRED** - GitHub OAuth Client ID |
| `OAUTH_GITHUB_CLIENT_SECRET` | - | **REQUIRED** - GitHub OAuth Client Secret |
| `OAUTH_REDIRECT_URI` | http://localhost:4200/auth/callback | OAuth redirect URL |
| `DB_NAME` | team_rocket_db | Database name |
| `DB_USERNAME` | team_rocket_admin | Database user |
| `DB_PASSWORD` | team_rocket_password123 | Database password |
| `DB_PORT` | 5432 | PostgreSQL port |
| `SERVER_PORT` | 8081 | Backend server port |
| `API_BASE_URL` | http://localhost:8081/api/v1 | API base URL |
| `FRONTEND_URL` | http://localhost:4200 | Frontend URL |
| `JWT_SECRET` | change-me-development-jwt-secret-1234567890 | JWT signing secret |
| `JWT_EXPIRATION_HOURS` | 8 | Token expiration time |

---

## Troubleshooting

### Port Already in Use
If you get "port already in use" errors:

```powershell
# Find process on port 8081
Get-NetTCPConnection -LocalPort 8081

# Kill process (replace PID with the OwningProcess value)
Stop-Process -Id <PID> -Force
```

### Database Connection Failed
```powershell
# Test PostgreSQL connection
psql -U team_rocket_admin -d team_rocket_db

# If password fails, reset it
psql -U postgres
ALTER USER team_rocket_admin WITH PASSWORD 'team_rocket_password123';
```

### GitHub OAuth Not Working
1. Verify `.env` file has correct `OAUTH_GITHUB_CLIENT_ID` and `OAUTH_GITHUB_CLIENT_SECRET`
2. Check GitHub OAuth app settings: https://github.com/settings/developers
3. Ensure authorization callback URL is exactly: `http://localhost:4200/auth/callback`

### Build Fails
```powershell
# Clean and rebuild
cd backend\rocket-trading
./mvnw clean install -DskipTests
```

### Frontend Not Connecting to Backend
1. Verify backend is running on port 8081
2. Check browser console for CORS errors
3. Verify `API_BASE_URL` in `.env` is correct
4. Check backend CORS configuration in `SecurityConfig.java`

---

## Production Considerations

**WARNING**: This setup is for **local development only**. For production:

1. **Change JWT_SECRET** to a strong random value
2. **Use HTTPS** (not HTTP)
3. **Set secure PostgreSQL password**
4. **Use environment-specific configuration** (dev, staging, production)
5. **Enable HTTPS** in GitHub OAuth settings
6. **Configure proper CORS origins** instead of `*`
7. **Use a secrets manager** (AWS Secrets Manager, HashiCorp Vault, etc.)
8. **Consider Docker or Kubernetes** for deployment

---

## Docker Alternative

If you prefer to run with Docker:

```bash
docker-compose up -d
```

See `docker-compose.yml` for configuration.

---

## Next Steps

1. Review the project documentation in `/docs`
2. Check API documentation at http://localhost:8081/api/v1/swagger-ui.html (if enabled)
3. Run tests: `./mvnw test` (backend) or `npm test` (frontend)
4. Set up your IDE debugger for better development experience

---

## Support

For issues or questions:
- Check project logs in `/target/logs` (backend)
- Check browser console (frontend)
- Review this guide's troubleshooting section
