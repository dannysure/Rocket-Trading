# 🚀 Rocket Trading - Full Stack Trading Application

A modern trading application built with Angular (frontend), Spring Boot (backend), and PostgreSQL (database), secured with GitHub OAuth 2.0.

## ⚡ Quick Links

- **🏃 Quick Start**: Read [QUICK_START.md](QUICK_START.md) (5 min setup)
- **📖 Detailed Setup**: Read [LOCAL_SETUP.md](LOCAL_SETUP.md) (comprehensive guide)
- **📝 What Changed**: Read [CHANGES_SUMMARY.md](CHANGES_SUMMARY.md)
- **🐳 Docker**: Run `docker-compose up -d`

---

## 🎯 What You'll Get

A fully functional trading platform with:

### Features
✅ **User Authentication**: GitHub OAuth 2.0 sign-in  
✅ **Portfolio Management**: Track holdings and performance  
✅ **Trading**: Place buy/sell orders with order matching  
✅ **Market Data**: Real-time stock and crypto quotes  
✅ **Responsive UI**: Modern Angular frontend  
✅ **RESTful API**: Complete Spring Boot REST API  
✅ **Persistent Data**: PostgreSQL database with migrations  

### Architecture
```
Angular Frontend (4200)
        ↓
REST API & OAuth Redirect (8081)
        ↓
PostgreSQL Database (5432)
```

---

## 📋 Prerequisites

Choose your setup method:

### Local Development (No Docker)
- Node.js 18+ (npm)
- Java 21 (for Spring Boot)
- PostgreSQL 16+
- Git

### Docker Setup
- Docker Desktop
- Docker Compose

---

## 🚀 Get Started

### Option A: Local Development (No Docker) - Recommended for Development

```bash
# 1. Clone and setup
git clone <repo>
cd Rocket-Trading
copy .env.example .env

# 2. Add GitHub OAuth credentials to .env
# Edit .env and add your GitHub OAuth Client ID & Secret
# Get these from: https://github.com/settings/developers

# 3. Setup database (PowerShell on Windows)
psql -U postgres
# CREATE USER team_rocket_admin WITH PASSWORD 'team_rocket_password123';
# CREATE DATABASE team_rocket_db OWNER team_rocket_admin;
# GRANT ALL PRIVILEGES ON DATABASE team_rocket_db TO team_rocket_admin;

# 4. Run migrations
cd backend/rocket-trading
./mvnw clean flyway:migrate -Dspring.datasource.url=jdbc:postgresql://localhost:5432/team_rocket_db -Dspring.datasource.username=team_rocket_admin -Dspring.datasource.password=team_rocket_password123
cd ../..

# 5. Start services in 3 terminals:

# Terminal 1 - Backend API
cd backend/rocket-trading
./mvnw clean spring-boot:run

# Terminal 2 - Frontend
cd frontend
npm install
npm start

# Terminal 3 - PostgreSQL (should be running)
# PostgreSQL should already be running as a service
```

✅ Open **http://localhost:4200** and sign in with GitHub!

See [LOCAL_SETUP.md](LOCAL_SETUP.md) for detailed instructions.

---

### Option B: Docker Setup - Recommended for Production

```bash
# 1. Setup
git clone <repo>
cd Rocket-Trading
copy .env.example .env

# 2. Add GitHub OAuth credentials to .env
# Edit .env and add your GitHub OAuth Client ID & Secret

# 3. Start all services
docker-compose up -d

# Wait 30 seconds for services to start
# PostgreSQL runs on port 5435 in Docker (configured in docker-compose.yml)

# Check logs
docker-compose logs -f
```

✅ Open **http://localhost:4200** and sign in with GitHub!

---

## ⚙️ Configuration

### GitHub OAuth Setup (Required)

1. Go to **https://github.com/settings/developers**
2. Click **OAuth Apps** → **New OAuth App**
3. Fill in:
   - **Application name**: Rocket Trading (Local)
   - **Homepage URL**: http://localhost:4200
   - **Authorization callback URL**: http://localhost:4200/auth/callback
4. Copy **Client ID** and **Client Secret**
5. Add to `.env`:
   ```env
   OAUTH_GITHUB_CLIENT_ID=your_client_id
   OAUTH_GITHUB_CLIENT_SECRET=your_client_secret
   OAUTH_REDIRECT_URI=http://localhost:4200/auth/callback
   ```

### Environment Variables

Copy `.env.example` to `.env` and configure:

| Variable | Default | Description |
|----------|---------|-------------|
| `OAUTH_GITHUB_CLIENT_ID` | - | **REQUIRED** - GitHub OAuth Client ID |
| `OAUTH_GITHUB_CLIENT_SECRET` | - | **REQUIRED** - GitHub OAuth Secret |
| `DB_NAME` | team_rocket_db | Database name |
| `DB_USERNAME` | team_rocket_admin | Database user |
| `DB_PASSWORD` | team_rocket_password123 | Database password |
| `SERVER_PORT` | 8081 | Backend port |
| `DB_PORT` | 5432 | PostgreSQL port (5435 in Docker) |
| `JWT_SECRET` | dev-secret | Change in production! |

---

## 🌐 Access Points

| Service | Local | Docker |
|---------|-------|--------|
| Frontend | http://localhost:4200 | http://localhost:4200 |
| Backend API | http://localhost:8081/api/v1 | http://localhost:8081/api/v1 |
| Database | localhost:5432 | localhost:5435 |

---

## 📚 Project Structure

```
Rocket-Trading/
├── frontend/                 # Angular 22 SPA
│   ├── src/
│   │   ├── app/            # Components & services
│   │   ├── index.html      # Entry point
│   │   └── main.ts         # Bootstrap
│   ├── package.json
│   └── Dockerfile
│
├── backend/
│   └── rocket-trading/      # Spring Boot 3.4 API
│       ├── src/
│       │   ├── main/
│       │   │   ├── java/   # Java source code
│       │   │   │   └── com/rockettrading/
│       │   │   │       ├── config/      # OAuth, Security, App config
│       │   │   │       ├── controller/  # REST endpoints
│       │   │   │       ├── service/     # Business logic
│       │   │   │       ├── repository/  # Database access
│       │   │   │       └── model/       # Entity classes
│       │   │   └── resources/
│       │   │       └── application.properties
│       │   └── test/
│       ├── pom.xml
│       ├── mvnw            # Maven wrapper
│       └── Dockerfile
│
├── database/               # Database migrations
│   ├── schema.sql         # Initial schema
│   └── migrations/        # Flyway migrations
│
├── .env.example           # Environment template (COPY THIS TO .env)
├── docker-compose.yml     # Docker multi-container setup
├── QUICK_START.md         # 5-minute setup guide
├── LOCAL_SETUP.md         # Detailed local setup
├── CHANGES_SUMMARY.md     # What changed and why
└── README.md             # This file
```

---

## 🔐 OAuth Flow

```
┌─────────────┐                    ┌──────────────┐              ┌─────────┐
│   Browser   │                    │ Rocket App   │              │ GitHub  │
└─────────────┘                    └──────────────┘              └─────────┘
       │                                  │                           │
       │ 1. Click "Sign in with GitHub"   │                           │
       ├─────────────────────────────────→│                           │
       │                                  │ 2. Redirect to GitHub    │
       │                                  ├──────────────────────────→│
       │                                  │                           │
       │ 3. User authorizes               │                           │
       │←──────────────────────────────────────────────────────────────┤
       │                                  │ 4. Auth code             │
       │                                  │←─────────────────────────┤
       │                                  │ 5. Exchange for token   │
       │                                  ├──────────────────────────→│
       │                                  │ 6. Access token          │
       │                                  │←─────────────────────────┤
       │                                  │ 7. JWT generated         │
       │ 8. Redirect with token          │                           │
       │←─────────────────────────────────┤                           │
       │                                  │                           │
       ✓ Logged In!                       ✓ User in DB               ✓
```

---

## 🛠️ Common Commands

### Local Development

```powershell
# Backend
cd backend/rocket-trading
./mvnw clean install              # Build
./mvnw spring-boot:run           # Run
./mvnw test                       # Run tests
./mvnw clean flyway:migrate       # Migrate database

# Frontend
cd frontend
npm install                        # Install deps
npm start                         # Start dev server
npm run build                     # Production build
npm test                          # Run tests

# Database
psql -U team_rocket_admin -d team_rocket_db   # Connect
```

### Docker

```bash
docker-compose up -d              # Start services
docker-compose down              # Stop services
docker-compose logs -f           # View logs
docker-compose restart           # Restart
docker-compose build             # Rebuild images
```

---

## 🔧 Troubleshooting

### Frontend Not Connecting to API
- ✅ Verify backend is running: `curl http://localhost:8081/api/v1/health`
- ✅ Check `.env` has correct API_BASE_URL
- ✅ Check browser console for errors

### GitHub OAuth Failed
- ✅ Verify `.env` has GitHub Client ID & Secret
- ✅ Check GitHub OAuth app settings: https://github.com/settings/developers
- ✅ Verify callback URL is exactly: `http://localhost:4200/auth/callback`

### Database Won't Connect
- ✅ Ensure PostgreSQL is running
- ✅ Check database credentials in `.env`
- ✅ Verify port: 5432 (local) or 5435 (Docker)

### Port Already in Use
```powershell
# Find process on port
netstat -ano | findstr :8081

# Kill process
taskkill /PID <process_id> /F
```

More troubleshooting: See [LOCAL_SETUP.md](LOCAL_SETUP.md#troubleshooting)

---

## 📖 Documentation

| Document | Purpose |
|----------|---------|
| [QUICK_START.md](QUICK_START.md) | 5-minute getting started guide |
| [LOCAL_SETUP.md](LOCAL_SETUP.md) | Detailed local development guide |
| [CHANGES_SUMMARY.md](CHANGES_SUMMARY.md) | What changed and why |
| [docs/API_DOCUMENTATION.md](docs/API_DOCUMENTATION.md) | API reference |
| [docs/ENV_SETUP.md](docs/ENV_SETUP.md) | Environment variables |

---

## 🔐 Security Notes

### Development Only ⚠️
This setup is **for local development only**.

### Production Requirements
- ❌ DO NOT use default passwords
- ❌ DO NOT use HTTP (use HTTPS)
- ❌ DO NOT commit `.env` file
- ✅ Use strong random JWT_SECRET
- ✅ Use HTTPS everywhere
- ✅ Use secrets manager (AWS Secrets Manager, Vault, etc.)
- ✅ Set secure database passwords
- ✅ Update GitHub OAuth to production domain

---

## 🚀 Deployment

### Using Docker
```bash
docker-compose -f docker-compose.prod.yml up -d
```

### To Kubernetes
1. Build images: `docker build -t rocket-trading-api backend/rocket-trading`
2. Create Kubernetes manifests
3. Configure secrets for OAuth and database
4. Deploy using `kubectl apply`

---

## 📦 Technology Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Frontend | Angular | 22+ |
| Backend | Spring Boot | 3.4+ |
| Language | Java | 21 |
| Database | PostgreSQL | 16+ |
| Build | Maven | 3.9+ |
| Node | Node.js | 18+ |
| Container | Docker | 20+ |

---

## 🤝 Contributing

1. Create a feature branch: `git checkout -b feature/amazing-feature`
2. Commit changes: `git commit -m 'Add amazing feature'`
3. Push to branch: `git push origin feature/amazing-feature`
4. Open a Pull Request

---

## 📝 License

This project is provided as-is for trading application development.

---

## ❓ Need Help?

1. Check [QUICK_START.md](QUICK_START.md) for quick setup
2. Read [LOCAL_SETUP.md](LOCAL_SETUP.md) for detailed instructions
3. Review [CHANGES_SUMMARY.md](CHANGES_SUMMARY.md) for recent changes
4. Check [troubleshooting section](#troubleshooting) above

---

## 🎉 Ready?

Start with [QUICK_START.md](QUICK_START.md) (5 minutes)  
Or jump to [LOCAL_SETUP.md](LOCAL_SETUP.md) (detailed guide)

Happy trading! 📈
