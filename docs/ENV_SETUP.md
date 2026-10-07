# Environment Configuration Setup

## Local Development Setup

Copy `.env` to your project root and configure these variables:

### Database Configuration
```env
DB_NAME=team_rocket_db              # PostgreSQL database name
DB_USERNAME=team_rocket_admin        # PostgreSQL user
DB_PASSWORD=team_rocket_password123  # PostgreSQL password (CHANGE IN PRODUCTION)
DB_PORT=5435                         # Docker port for PostgreSQL
```

### JWT Configuration (Legacy - Use OAuth in production)
```env
JWT_SECRET=your-secure-secret-here   # Secret key for JWT signing
JWT_ISSUER=rocket-trading            # JWT issuer identifier
JWT_EXPIRATION_HOURS=8               # Token expiration time
```

### Kafka Configuration
```env
KAFKA_BOOTSTRAP_SERVERS=localhost:9092   # Kafka broker addresses
```

### API Server Configuration
```env
SERVER_PORT=8081                     # Java backend port
API_BASE_URL=http://localhost:8081/api/v1  # API base URL for frontend
FRONTEND_URL=http://localhost:4200   # Frontend URL (CORS)
```

### Quote Provider Configuration
```env
FAUXNANCE_API_KEY=demo-key-change-this              # Mock quote provider API key
FAUXNANCE_BASE_URL=https://y4t9nq2bqf.execute-api.eu-west-2.amazonaws.com
FAUXNANCE_STOCK_PATH=/v1/quotes/{symbol}
FAUXNANCE_CRYPTO_PATH=/v1/quotes/{symbol}
FAUXNANCE_MAX_AGE_SECONDS=900               # Max quote age for pricing; 15 min = standard delayed data
FAUXNANCE_ALLOW_STALE=true                  # Accept provider-flagged stale quotes within max age (audited)
```

### Trading Worker Configuration
```env
TRADING_WORKER_ENABLED=true         # Enable automated order matching
TRADING_WORKER_DELAY_MS=1000        # Delay between worker iterations (ms)
```

### OAuth Configuration (PRODUCTION)

#### Google OAuth
```env
OAUTH_PROVIDER=google
OAUTH_GOOGLE_CLIENT_ID=xxxxx.apps.googleusercontent.com
OAUTH_GOOGLE_CLIENT_SECRET=xxxxx
OAUTH_GOOGLE_AUTH_URI=https://accounts.google.com/o/oauth2/v2/auth
OAUTH_GOOGLE_TOKEN_URI=https://oauth2.googleapis.com/token
OAUTH_GOOGLE_USERINFO_URI=https://openidconnect.googleapis.com/v1/userinfo
```

#### GitHub OAuth
```env
OAUTH_PROVIDER=github
OAUTH_GITHUB_CLIENT_ID=xxxxx
OAUTH_GITHUB_CLIENT_SECRET=xxxxx
OAUTH_GITHUB_AUTH_URI=https://github.com/login/oauth/authorize
OAUTH_GITHUB_TOKEN_URI=https://github.com/login/oauth/access_token
OAUTH_GITHUB_USERINFO_URI=https://api.github.com/user
```

#### Microsoft/Azure AD
```env
OAUTH_PROVIDER=microsoft
OAUTH_MICROSOFT_CLIENT_ID=xxxxx
OAUTH_MICROSOFT_CLIENT_SECRET=xxxxx
OAUTH_MICROSOFT_TENANT=common
OAUTH_MICROSOFT_AUTH_URI=https://login.microsoftonline.com/{tenant}/oauth2/v2.0/authorize
OAUTH_MICROSOFT_TOKEN_URI=https://login.microsoftonline.com/{tenant}/oauth2/v2.0/token
OAUTH_MICROSOFT_USERINFO_URI=https://graph.microsoft.com/v1.0/me
```

#### Frontend OAuth Configuration
```env
OAUTH_REDIRECT_URI=http://localhost:4200/auth/callback
OAUTH_SCOPE=openid profile email
```

## Security Best Practices

⚠️ **CRITICAL**: Never commit `.env` to version control
⚠️ **Production**: Use a secrets manager (AWS Secrets Manager, HashiCorp Vault, Azure Key Vault)
⚠️ **Passwords**: Change all default database credentials in production
⚠️ **Keys**: Rotate JWT_SECRET and OAuth client secrets regularly
⚠️ **HTTPS**: Use HTTPS in production (not HTTP)

## Docker Compose Environment

All services read from `.env` file automatically via Docker Compose:

```bash
# Start services with environment from .env
docker compose up -d

# Services automatically inject environment variables
# - PostgreSQL: DB_NAME, DB_USERNAME, DB_PASSWORD, DB_PORT
# - Kafka: KAFKA_BOOTSTRAP_SERVERS (read by Java backend)
# - Java Backend: All SERVER_*, API_*, JWT_*, KAFKA_*, FAUXNANCE_*, TRADING_*
# - Angular Frontend: API_BASE_URL, FRONTEND_URL, OAUTH_* (via build)
```

## Local Testing Without Docker

If running services locally:

```bash
# Backend (requires Java 21 + Maven 3.9)
cd backend/rocket-trading
./mvnw spring-boot:run

# Frontend (requires Node 22 + npm)
cd frontend
npm install
npm start

# Database (requires PostgreSQL 15)
psql -h localhost -p 5432 -U team_rocket_admin -d team_rocket_db
```

## Environment Validation

Before starting services, verify:

```bash
# Check database
psql -h localhost -p 5432 -U $DB_USERNAME -d $DB_NAME -c "SELECT 1"

# Check Kafka
kafka-broker-api-versions.sh --bootstrap-server localhost:9092

# Check API server
curl http://localhost:8081/health

# Check Frontend
curl http://localhost:4200
```

## Troubleshooting Environment Issues

**Port already in use**: Change port in `.env` and docker-compose.yml
**Database connection failed**: Verify DB_USERNAME, DB_PASSWORD, DB_PORT
**OAuth redirect failed**: Ensure OAUTH_REDIRECT_URI matches OAuth provider settings
**API not found**: Check API_BASE_URL matches your backend URL
