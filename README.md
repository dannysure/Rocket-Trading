# Rocket Trading

Rocket Trading is a Spring Boot + Angular direct trading demo built around the LEAP business requirements. The current implementation includes:

- fixture-backed registration and JWT sign-in
- Fauxnance-powered stock and crypto quote lookup
- transactional order placement against Postgres
- portfolio summary and order history endpoints
- a simple Angular UI for testing the main flows end to end

## Repository structure

- [backend/rocket-trading/](C:/Users/Administrator/Downloads/Rocket-Trading/backend/rocket-trading) - Spring Boot API, MyBatis repositories, JWT security, and tests
- [frontend/rocket-trading-ui/](C:/Users/Administrator/Downloads/Rocket-Trading/frontend/rocket-trading-ui) - Angular test UI
- [schema.sql](C:/Users/Administrator/Downloads/Rocket-Trading/schema.sql) - shared Postgres schema for local Docker database setup

## Backend setup

1. Copy [.env.example](C:/Users/Administrator/Downloads/Rocket-Trading/backend/rocket-trading/.env.example) to `backend/rocket-trading/.env`.
2. Set at least:
   - `JWT_SECRET`
   - `FAUXNANCE_API_KEY`
   - optional Postgres overrides if you want a real local database
3. Start Postgres from either:
   - repository root: `docker compose up -d db`
   - backend module: `cd backend\\rocket-trading && docker compose up -d postgres`
4. Run the API:

   ```bat
   cd backend\rocket-trading
   mvn spring-boot:run
   ```

The API listens on `http://localhost:8081`.

### No-virtualization fallback

If Docker Desktop cannot run because virtualization is unavailable, the backend now defaults to an embedded H2 database automatically.

That means you can run:

```bat
cd backend\rocket-trading
mvn spring-boot:run
```

without Docker or PostgreSQL installed.

Spring Boot's Docker Compose integration is also disabled by default for this local mode, so the backend will not try to auto-start [compose.yaml](C:/Users/Administrator/Downloads/Rocket-Trading/backend/rocket-trading/compose.yaml) unless you explicitly enable it.

Optional local database inspection:

- H2 console: `http://localhost:8081/h2-console`
- JDBC URL: `jdbc:h2:mem:rockettrading`
- Username: `sa`
- Password: leave blank

## Docker install on Windows

If PowerShell says `docker : The term 'docker' is not recognized`, Docker Desktop is not installed or not on your PATH.

Recommended install path on Windows:

1. Install **Docker Desktop for Windows** from:
   - https://www.docker.com/products/docker-desktop/
2. Make sure **WSL 2** is enabled when the installer prompts you.
3. Reboot if Windows asks.
4. Open Docker Desktop once and wait for it to finish starting.
5. Open a **new** PowerShell window and verify:

   ```powershell
   docker --version
   docker compose version
   ```

If WSL is missing, install it in an elevated PowerShell first:

```powershell
wsl --install
```

Then reboot and install Docker Desktop again if needed.

## Docker run from repository root

Once Docker Desktop is installed and running:

```powershell
cd C:\Users\Administrator\Downloads\Rocket-Trading
docker compose up --build
```

That root compose file now starts:

- `db` on port `5432`
- `api` on port `8081`
- `ui` on port `4200`

Then open:

- UI: `http://localhost:4200`
- API: `http://localhost:8081`

## Frontend setup

Run the Angular UI in a second terminal:

```bat
cd frontend\rocket-trading-ui
npm install
npm start
```

The UI runs on `http://localhost:4200`.

## Useful backend endpoints

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/sign-in`
- `POST /api/v1/auth/sign-out`
- `GET /api/v1/quotes/{symbol}?market=stock|crypto`
- `GET /api/v1/portfolio/summary`
- `POST /api/v1/orders`
- `GET /api/v1/orders`

## Tests

Backend tests:

```bat
cd backend\rocket-trading
mvn test
```

Frontend production build:

```bat
cd frontend\rocket-trading-ui
npm run build
```

## Notes

- The current auth flow is intentionally stub-friendly for sprint delivery: you register a client profile, then sign in with that email.
- Order placement executes immediately and persists order, fill, ledger, holding, and audit changes inside one Spring transaction.
- The Fauxnance client is implemented with a best-effort JSON mapping. If your API returns a different payload shape, send me one example response and I can tighten the parser quickly.