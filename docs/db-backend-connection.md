# Database connection and teammate guide

## What changed

Branch: `feature/db-backend-connection`, based on `develop`.

The existing Angular → Spring Boot → MyBatis flow now uses PostgreSQL exclusively.
H2, its console and its fallback settings are removed. The root Compose file is the
single startup entry point; SQL migrations in the Java module are the schema source of truth.
Flyway replaces the old startup script that dropped and rebuilt tables.

Order submission records intent and commits `ACCEPTED` before execution. A scheduled
worker in the same application reads accepted orders every second. It gets a new quote,
rechecks trading rules and settles cash, account/client holdings, fill, ledger and audit
in one transaction. Account/order locks serialize competing changes. Each order has at
most one full fill. A database failure rolls back settlement, leaving the accepted order
for the worker to retry, including after an application restart.

Business failures, including missing/stale prices, result in a stored `REJECTED` order
with a reason. Provider failure at submission rejects the attempted order; it is not
silently queued for later pricing. A LIMIT order is checked at acceptance and execution;
this phase does not offer standing limit orders waiting for a future price.

Order requests require an `Idempotency-Key`. Reusing the same key and normalized payload
returns the same order; a changed payload gets `409 IDEMPOTENCY_CONFLICT`. Keys are scoped
to the signed-in client. Angular retains the key after an uncertain network/server error
so retrying the same order does not create another trade. Completed requests get a fresh
key for the next intentional order.

The UI polls pending orders every two seconds and shows filled prices or rejection reasons.
It refreshes the portfolio automatically and stops polling on settlement/sign-out.
Fill history now checks ownership, and portfolio reads use a consistent database snapshot.
Audit, fills, quote snapshots and ledger rows reject updates/deletes; order deletion is
blocked. These protections do not prevent a database administrator from altering the database.

## Start the application

Requirements for the Docker route: Docker Desktop (running), Compose v2.24.4+, and a
quote-provider API key for live quote/trading calls. Java and Node run inside the images.
Only local loopback ports are exposed because authentication is still a fixture.

After the branch has been shared remotely, teammates can fetch and switch to it:

```sh
git fetch origin
git switch feature/db-backend-connection
cp .env.example .env
```

PowerShell: `Copy-Item .env.example .env`. Set `FAUXNANCE_API_KEY` in `.env` and check the
provider base URL and stock/crypto paths against the supplied provider configuration.
Never commit `.env`. Values there configure the root Compose stack.

```sh
docker compose up --build -d
docker compose logs -f api
```

Wait for the API's `Started RocketTradingApplication` message.

| Service | Address |
| --- | --- |
| Browser UI | http://localhost:4200 |
| Spring API | http://localhost:8081/api/v1 |
| PostgreSQL | localhost:5435; database/user/password from `.env` |

Register a new fixture client, sign in using its email, fetch AAPL, and submit a small buy.
Watch the order change to `FILLED` and the balance/holding update without pressing Refresh.
Supported catalogue: AAPL, MSFT, GOOGL, BTCUSD and ETHUSD. All are USD denominated in this phase.

Without a working API key you can still register/sign in and view the portfolio; quote
lookup fails and attempted orders are recorded as rejected. Automated tests use stub prices
and need no provider key.

```sh
docker compose down         # Stop services; retain the volume
docker compose up -d        # Resume existing data
docker compose up --build -d # Rebuild after pulling code; retain the volume
```

Do not use `down -v`, prune database volumes or delete data directories to resolve startup
errors. Those actions erase stored clients and trades.

## Native development

Install Java 21, Node.js 22 and Docker. Use the Maven wrapper; Maven installation is optional.
From the repository root, start only PostgreSQL:

```sh
docker compose up -d db
cd backend/rocket-trading
./mvnw spring-boot:run
```

In another terminal:

```sh
cd frontend/rocket-trading-ui
npm ci
npm start
```

On Windows, substitute `mvnw.cmd` for `./mvnw`. The backend reads the root `.env` when
started from its module directory. If you change `DB_NAME` or `DB_PORT`, also set `DB_URL`
for a native backend, for example `jdbc:postgresql://localhost:5436/my_database`.
Compose always sets its own internal database URL using the `db` service hostname.

Angular's development proxy and the container's nginx proxy both forward `/api` to Spring.
Do not run native API/UI servers and their Compose counterparts on the same ports together.

## Run the tests

Fast unit tests (no database):

```sh
cd backend/rocket-trading
./mvnw test
```

Unit + integration tests, with Docker running:

```sh
./mvnw verify
```

Integration tests use disposable PostgreSQL 15 containers and the real Flyway migrations.
They do not connect to the Compose/developer database. Only the external quote source is
stubbed. Testcontainers 1.21.4 supports current Docker API versions.

Frontend tests and production build:

```sh
cd frontend/rocket-trading-ui
npm ci
npm run test:ci
npm run build
```

`test:ci` needs Chrome. Set `CHROME_BIN` if it is not found automatically. On macOS:

```sh
CHROME_BIN="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" npm run test:ci
```

For browser tests or a deterministic local demo, stop the normal UI/API stack first.
From the root:

```sh
docker compose -p rocket-trading-e2e -f docker-compose.yml -f compose.e2e.yaml up --build -d
```

This uses a separate project/volume and a test-only HTTP quote server (bid 100, ask 101).
It exposes the same UI/API ports, but does not expose its database port. Wait for startup
in `docker compose -p rocket-trading-e2e -f docker-compose.yml -f compose.e2e.yaml logs api`.
Then:

```sh
cd frontend/rocket-trading-ui
npm ci
npx playwright install chromium
npm run test:e2e
```

Afterward, from the root:

```sh
docker compose -p rocket-trading-e2e -f docker-compose.yml -f compose.e2e.yaml down
```

Tests create fresh clients on each browser run. Their volume is intentionally distinct
from the normal `rocket-trading` project. Never treat fixture prices as market prices.

GitHub Actions runs Java verification, frontend unit tests/build and browser tests on PRs.
The Jenkinsfile runs the same layers; its agent needs Java 21, Node 22, Docker and Chrome,
and a dedicated workspace/ports for the test stack. CI configuration is included; local
verification does not imply a remote CI run has already completed.

### Verified locally on 28 September 2026

- Backend: 376 database-free unit tests and 14 PostgreSQL integration tests passed,
  with no failures, errors or skipped tests.
- Frontend: 6 unit tests and the production build passed.
- Chromium: the complete registration/sign-in, buy, automatic settlement, portfolio/history,
  reload, sell, rejection and sign-out journey passed against the containerized stack.
- Fresh database startup applied all three migrations. A full Compose stop/remove/start
  retained all 4 test orders, 3 fills, combined cash of 19,190 and 27 audit rows unchanged.
  The restarted application validated the migrations and started successfully.

Quotes were supplied by test fixtures. The real provider credentials and remote CI run
have not been verified. The isolated test stack was stopped afterward, retaining its volume.

## Existing databases and safe migration

A fresh, empty database migrates automatically. Existing Flyway-managed databases apply
only pending migrations. Normal startup never drops or recreates trading tables.

For a non-empty database created by the older scripts, startup deliberately fails until
someone reviews it. Automatic baselining is disabled. Before adopting that database:

1. Stop the old application/writers and take a backup. Restore the backup into a separate
   PostgreSQL instance and work on that copy first. Keep the original volume intact.
2. Compare its schema to `V1__trading_schema.sql`. The older `database/schema.sql` prototype
   differs from the Java schema and must not be blindly baselined. Inspect existing
   duplicate client emails, direct-trading accounts and multiple fills per order; V2 adds
   constraints that deliberately fail on incompatible data instead of deleting it.
3. Inspect triggers. V2 refuses to proceed if `trigger_fill_execution` exists. A reviewed
   cutover must disable the older settlement/audit mechanisms before Java owns writes;
   simply removing the guard is unsafe. Historical pending orders also need explicit review
   because the worker executes every `ACCEPTED` order after startup.
4. Only after confirming the restored database matches V1, explicitly record a Flyway
   baseline at version 1. Use Flyway 10.20.1 tooling with credentials for the restored copy,
   for example Maven's `org.flywaydb:flyway-maven-plugin:10.20.1:baseline` goal with its
   `flyway.url`, `flyway.user`, `flyway.password` and `flyway.baselineVersion=1` settings.
   Run this from the backend module. Do not put real passwords in shared shell history.
5. Start this backend against the restored copy, verify its records and balances, and rehearse
   the cutover before doing the same controlled migration on the original database.

Old timestamp-without-time-zone values are interpreted as UTC by V2; verify that assumption
against any historical data before baselining. The migration test covers a V1-compatible
schema adoption and proves client records survive. It does not certify arbitrary legacy data.

The old Python API and `database/br09_fill_atomicity.sql` are not part of this startup path.
Do not apply that trigger script to the new trading database: Java already performs settlement.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| PostgreSQL connection fails | Start Docker/database; check URL, port and credentials. There is no fallback database. |
| Port in use | Change `DB_PORT` for PostgreSQL; stop only your conflicting API/UI process or adjust its ports. |
| Non-empty schema / missing history error | Follow the migration review above; do not reset or blindly baseline. |
| `QUOTE_PROVIDER_NOT_CONFIGURED` / order rejected | Set the real provider key, rebuild/recreate the API and verify provider paths. |
| Stale or missing quote timestamp | Inspect the provider payload; the app no longer turns old timestamps into fresh ones. |
| `Idempotency-Key` validation error | Custom API callers must provide the header; the UI adds it automatically. |
| Order remains accepted | Inspect API logs for settlement rollback/database failures; the worker retries automatically. |
| Browser gets 502 immediately after startup | Wait for API startup/migrations; inspect API logs if it persists. |
| No Docker environment during `verify` | Start Docker Desktop; integration tests intentionally fail instead of silently skipping. |

## BRD coverage in this phase

| Requirement | Evidence / remaining gap |
| --- | --- |
| BR-01 | Registration/sign-in tested; email fixture is not secure production identity. |
| BR-02–03 | HTTP tests cover own-data access, fill ownership, session expiry and revocation. |
| BR-04–05 | Buy/sell, input validation, supported instruments, tradability, cash and holding checks. |
| BR-06 | Acceptance is committed separately; restart test recovers an accepted order. |
| BR-07 | Browser/unit tests verify automatic polling and settlement display. |
| BR-08–09 | Fresh execution quote, limit recheck, atomic rollback, concurrency and duplicate tests. |
| BR-10–11 | Portfolio, order history and fills tested; restart preserves balances. |
| BR-12 | Partial: USD stock/crypto catalogue only; FX and broader market/currency coverage deferred. |
| BR-13 | Indicative quote lookup exercised in the browser. |
| BR-14–15 | Durable records, pricing evidence, balance changes and append-only protection; operational audit access and full retention controls deferred. |
| BR-16–18 | Isolated reporting, insights and additional capability deferred. |

Other limits: one direct-trading account and one full fill per order, no real banking,
no reservations for pending orders (balances are rechecked at execution), and no standing
limit-order book. IDs remain JSON int64 numbers; very large client IDs may lose display
precision in JavaScript, while authorization uses the signed token. This phase is a local
core-mechanics demo, not a complete production or regulatory implementation.

## Teammate announcement

> PostgreSQL integration is ready on `feature/db-backend-connection`. H2 is removed.
> Use the root Compose file; migrations now preserve data across restarts. Orders are
> accepted first and executed by a background worker, and the UI automatically updates
> status, holdings and fills. Custom order callers must send `Idempotency-Key`.
>
> Once the branch is shared: fetch/switch to it, copy `.env.example` to `.env`, set the
> supplied quote API key, then run `docker compose up --build -d` from the repository root.
> Open http://localhost:4200. PostgreSQL is on port 5435. Read this guide before connecting
> an existing database; do not reset its volume or run the legacy fill-trigger SQL.
>
> Run `./mvnw verify` in the backend module with Docker running for the unit/database
> tests. Frontend and browser commands are above. Sign-in remains a local email fixture;
> reporting and broader BRD features remain separate work.
