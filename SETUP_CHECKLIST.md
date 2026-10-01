# Rocket Trading - Getting Started Checklist

Use this checklist to track your setup progress. Check off items as you complete them.

---

## ✅ Pre-Setup Checklist

- [ ] I have Node.js installed (`node --version` shows 18+)
- [ ] I have Java 21 installed (`java --version` shows 21)
- [ ] I have PostgreSQL installed (`psql --version` shows 16+)
- [ ] I have Git installed (`git --version` works)
- [ ] I have a GitHub account (for OAuth)
- [ ] I'm comfortable with command line/terminal
- [ ] I have VS Code or IDE of choice installed
- [ ] I have enough disk space (2GB free minimum)

---

## 🔐 GitHub OAuth Setup - MUST DO FIRST!

### Create GitHub OAuth App
- [ ] Go to https://github.com/settings/developers
- [ ] Click "OAuth Apps" → "New OAuth App"
- [ ] Fill in application details:
  - [ ] Application name: `Rocket Trading (Local)`
  - [ ] Homepage URL: `http://localhost:4200`
  - [ ] Authorization callback URL: `http://localhost:4200/auth/callback`
- [ ] Click "Register application"
- [ ] Copy **Client ID** to `.env`
- [ ] Copy **Client Secret** to `.env`
- [ ] Verify .env has both values set (don't skip this!)

---

## 📁 Project Setup

- [ ] Cloned repository or extracted project
- [ ] Navigated to project root directory
- [ ] Copied `.env.example` to `.env`
- [ ] Edited `.env` with GitHub OAuth credentials
- [ ] Verified `.env` file exists in root directory
- [ ] Confirmed `.env` is NOT committed (in `.gitignore`)

---

## 🗄️ Database Setup

### Create PostgreSQL User and Database
- [ ] Opened PowerShell or terminal
- [ ] Connected to PostgreSQL: `psql -U postgres`
- [ ] Created database user:
  ```sql
  CREATE USER team_rocket_admin WITH PASSWORD 'team_rocket_password123';
  ```
- [ ] Created database:
  ```sql
  CREATE DATABASE team_rocket_db OWNER team_rocket_admin;
  ```
- [ ] Granted privileges:
  ```sql
  GRANT ALL PRIVILEGES ON DATABASE team_rocket_db TO team_rocket_admin;
  ```
- [ ] Exited psql: `\q`

### Run Database Migrations
- [ ] Navigated to `backend/rocket-trading`
- [ ] Ran Flyway migrations:
  ```
  ./mvnw clean flyway:migrate -Dspring.datasource.url=jdbc:postgresql://localhost:5432/team_rocket_db -Dspring.datasource.username=team_rocket_admin -Dspring.datasource.password=team_rocket_password123
  ```
- [ ] Verified migrations ran successfully (look for "Successfully applied")
- [ ] Returned to project root: `cd ../..`

---

## 🚀 Start Services

### Terminal 1 - Backend API Server
- [ ] Opened new terminal/PowerShell window
- [ ] Navigated to `backend/rocket-trading`
- [ ] Started backend:
  ```bash
  ./mvnw clean spring-boot:run
  ```
- [ ] Waited for startup (2-5 minutes first time)
- [ ] Verified: "Server listening on port 8081"
- [ ] **LEFT TERMINAL RUNNING** (don't close it)

### Terminal 2 - Frontend Dev Server
- [ ] Opened new terminal/PowerShell window
- [ ] Navigated to `frontend`
- [ ] Installed dependencies (first time only):
  ```bash
  npm install
  ```
- [ ] Started frontend:
  ```bash
  npm start
  ```
- [ ] Waited for compilation (1-2 minutes)
- [ ] Verified: "Local: http://localhost:4200"
- [ ] **LEFT TERMINAL RUNNING** (don't close it)

### Terminal 3 - Database
- [ ] Verified PostgreSQL is running (it should be a background service)
- [ ] If needed, started PostgreSQL:
  ```bash
  net start postgresql-x64-16
  ```
- [ ] Left terminal available if needed for database commands

---

## ✨ Verify Everything Works

### Test Frontend
- [ ] Opened web browser
- [ ] Navigated to **http://localhost:4200**
- [ ] Saw Rocket Trading login page
- [ ] Found "Sign in with GitHub" button

### Test OAuth Login
- [ ] Clicked "Sign in with GitHub" button
- [ ] Was redirected to GitHub login
- [ ] Entered GitHub username and password
- [ ] Clicked "Authorize" button
- [ ] Was redirected back to app
- [ ] Saw dashboard after login
- [ ] Verified user profile information displayed

### Test Backend API
- [ ] Opened new terminal
- [ ] Tested health endpoint:
  ```bash
  curl http://localhost:8081/api/v1/health
  ```
- [ ] Received successful response

### Test Database Connection
- [ ] In terminal, connected to database:
  ```bash
  psql -U team_rocket_admin -d team_rocket_db
  ```
- [ ] Ran query:
  ```sql
  SELECT * FROM client LIMIT 1;
  ```
- [ ] Saw your user record in database
- [ ] Exited database: `\q`

---

## 🎉 Success! You're Done!

If you've checked all the boxes above, your local development environment is ready!

### What's Running
- ✅ Frontend at http://localhost:4200
- ✅ Backend API at http://localhost:8081/api/v1
- ✅ Database at localhost:5432
- ✅ GitHub OAuth authentication working
- ✅ User data persisting in database

---

## 🚀 Next Steps

### Start Developing
1. Make changes to code
2. Refresh browser (frontend auto-reloads)
3. Backend auto-recompiles on changes
4. Test your changes

### Useful Commands

```bash
# Stop all services
# Press Ctrl+C in each terminal window

# Restart backend
./mvnw clean spring-boot:run

# Restart frontend
npm start

# Reset database
psql -U team_rocket_admin -d team_rocket_db
DROP TABLE <table_name>;
# Then restart backend to re-run migrations

# View backend logs
cat logs/application.log

# View database size
psql -U team_rocket_admin -d team_rocket_db
\dt+  # shows tables and sizes
```

### Useful Links
- Frontend code: `frontend/src/app/`
- Backend code: `backend/rocket-trading/src/main/java/`
- Database schema: `database/schema.sql`
- API docs: See `docs/API_DOCUMENTATION.md`
- OAuth setup: See `docs/OAUTH_SETUP.md`

---

## 🆘 Troubleshooting Quick Reference

| Problem | Solution |
|---------|----------|
| "Port 8081 already in use" | Kill the process: `netstat -ano \| findstr :8081` then `taskkill /PID <id> /F` |
| "Cannot connect to database" | Ensure PostgreSQL is running: `net start postgresql-x64-16` |
| "GitHub OAuth not working" | Check `.env` has correct Client ID and Secret from GitHub |
| "npm command not found" | Install Node.js from https://nodejs.org/ |
| "Java not found" | Install Java 21 from https://www.oracle.com/java/technologies/downloads/ |
| "Frontend not connecting to backend" | Check backend is running on 8081 and check browser console for errors |

For more help, see [LOCAL_SETUP.md](LOCAL_SETUP.md#troubleshooting)

---

## 📚 Documentation

When you need help:
1. **Quick questions**: See [QUICK_START.md](QUICK_START.md)
2. **Detailed setup**: See [LOCAL_SETUP.md](LOCAL_SETUP.md)
3. **What changed**: See [CHANGES_SUMMARY.md](CHANGES_SUMMARY.md)
4. **API reference**: See [docs/API_DOCUMENTATION.md](docs/API_DOCUMENTATION.md)

---

## ⏱️ Expected Timeline

- OAuth setup: 5 minutes
- Database setup: 5 minutes
- First start (with npm install): 10-15 minutes
- Verification: 5 minutes
- **Total: ~30 minutes**

Subsequent startups should take < 2 minutes (no npm install needed).

---

## 💡 Pro Tips

1. **Keep 3 terminals open** for backend, frontend, and database
2. **Use IDE** with Spring Boot and Angular extensions for better development
3. **Check `.env` before starting** - missing OAuth credentials is the most common issue
4. **Browser DevTools** (F12) are your friend for frontend debugging
5. **Backend logs** show OAuth flow details when debugging auth issues

---

## 🎊 Ready to Start?

Go to [QUICK_START.md](QUICK_START.md) or [LOCAL_SETUP.md](LOCAL_SETUP.md) and let's get coding!

Good luck! 🚀
