# Rocket Trading

An Angular + Spring Boot + MyBatis trading demo, backed exclusively by PostgreSQL.
Accepted orders are saved before a background worker executes them. Settlement updates
cash, holdings, fills, the ledger and audit together. PostgreSQL data survives restarts;
Flyway applies versioned schema changes without resetting tables.

## Run locally

Install Docker Desktop with Compose v2.24.4 or later and start Docker.
From the repository root:

```sh
git switch feature/db-backend-connection
cp .env.example .env
# Set FAUXNANCE_API_KEY in .env to your supplied quote-provider key.
docker compose up --build -d
```

PowerShell: use `Copy-Item .env.example .env` instead of `cp`.
Open http://localhost:4200. API: http://localhost:8081. PostgreSQL: localhost:5435.
Register a fixture client, sign in with its email, then submit a supported order.
The UI follows accepted orders until they fill or reject and shows the resulting cash,
holdings and fill prices. The starting catalogue is AAPL, MSFT, GOOGL, BTCUSD and ETHUSD.

A valid quote-provider key, paths and current timestamped quotes are needed for live orders.
Without them, registration/sign-in work and attempted orders are recorded as rejected.
For a deterministic demo without an API key, use the isolated browser-test stack in the guide.

```sh
docker compose logs -f api       # Diagnose startup or rejected quotes
docker compose down              # Stop; keeps the database volume
docker compose up -d             # Resume with existing data
```

**Existing database:** read the migration section before switching an older installation.
Never remove its volume or enable automatic baselining to bypass a migration error.

## Development and tests

For native development/tests: Java 21, Node.js 22, and Docker. Maven is supplied by the wrapper.

```sh
cd backend/rocket-trading
./mvnw test                      # Fast unit tests, no database
./mvnw verify                    # Also runs real PostgreSQL integration tests; Docker required
```

On Windows use `mvnw.cmd` instead of `./mvnw`.

```sh
cd frontend/rocket-trading-ui
npm ci
npm run test:ci                  # Requires installed Chrome, or CHROME_BIN
npm run build
```

See [the connection and teammate guide](docs/db-backend-connection.md) for native app startup,
browser tests, safe migration, troubleshooting and a copyable team announcement.
See [the API guide](docs/api/README.md) and [OpenAPI contract](docs/api/openapi.yaml) for requests.

## Scope

This is a local fixture demo: email-only sign-in and user-selected starting cash are not
production authentication or banking. The PostgreSQL connection and core transaction tests
are implemented; full BRD compliance also needs secure identity, wider market/currency
support, isolated reporting, operational audit access and an additional capability.

The older Python `api/`, `database/` scripts and `tests/test_*.py` are a separate prototype.
They are not started or applied by this Spring application. The authoritative trading schema
is `backend/rocket-trading/src/main/resources/db/migration/`.
