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


# Running the Frontend natively
	cd frontend
	npm install
	npm start

The portfolio dashboard talks directly to the Spring API at `http://localhost:8081/api/v1`.
The optional `frontend/.env` is only for the homepage quote proxy in [server.ts](frontend/src/server.ts),
for example `MARKET_DATA_API_KEY=your_api_key_here`.

PowerShell: use `Copy-Item .env.example .env` instead of `cp`.
Open http://localhost:4200. API: http://localhost:8081/api/v1. PostgreSQL: localhost:5435.
The checked-in Angular app lives in [frontend/](frontend). Clients get a dashboard, trade ticket and
blotter (orders poll for status changes and show the audit timeline); analysts and operations get
Insights and Trade lookup.
The starting catalogue is AAPL, MSFT, GOOGL, BTCUSD and ETHUSD.

A valid quote-provider key is needed to price orders. Sign-in is email and password; GitHub sign-in
is offered too when `OAUTH_GITHUB_CLIENT_ID`/`SECRET` are set. Set `FIXTURE_PASSWORD` in `.env` to get
the demo accounts: `joanna@example.com` (client), `priya@rocket.internal` (analyst: Insights) and
`david@rocket.internal` (operations: Insights and Trade lookup).

Instruments cover US, UK (US listings) and Indian equities, FX and crypto; everything settles in the
USD account at the FX rate recorded on the fill. An order whose market has no current price (e.g. NSE
outside its hours) is accepted and waits, retrying every `TRADING_DEFERRAL_RETRY_SECONDS`, and expires
after `TRADING_DEFERRAL_MAX_HOURS`. Internal reporting reads a snapshot refreshed every
`REPORTING_REFRESH_MS`, never the live order tables.
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
cd frontend
npm ci
npm test -- --watch=false
npm run build
```


Start containerized application on linux vm:
clone the repo into linux vm
```sh
cd Rocket-Trading/

# Set FAUXNANCE_API_KEY in .env 
cp.env.example .env
sed -i 's/^FAUXNANCE_API_KEY=.*/FAUXNANCE_API_KEY=your_real_api_key_here/' .env
grep FAUXNANCE_API_KEY .env

#start dockerized application
sudo systemctl start docker
sudo systemctl enable docker
sudo systemctl status docker
docker-compose up --build -d

#enable ssh port forwarding to access the ui, api, and db from your local machine
ssh -L 4200:127.0.0.1:4200 -L 8081:127.0.0.1:8081 -L 5435:127.0.0.1:5435 ec2-user@YOUR_VM_IP

#open your browser and go to http://localhost:4200 to access the UI

#check changes in postgres db
docker-compose exec db psql -U team_rocket_admin -d team_rocket_db
```


See [the connection and teammate guide](docs/db-backend-connection.md) for native app startup,
browser tests, safe migration, troubleshooting and a copyable team announcement.
See [the API guide](docs/api/README.md) and [OpenAPI contract](docs/api/openapi.yaml) for requests.

## Scope

This is a local demo: practice cash is modelled in-platform (no banking integration), and the
fixture accounts must never be enabled in a shared environment. Roles limit access: clients see
only their own book, analysts see aggregate reporting, operations can trace any trade (every lookup
is itself logged). The additional capability (BR-18) is the client watchlist.

The authoritative trading schema is `backend/rocket-trading/src/main/resources/db/migration/`.
