# Trading tests

From `backend/rocket-trading`:

- `./mvnw test`: database-free domain, validation, quote, auth and security unit tests.
- `./mvnw verify`: unit tests plus `*IT` PostgreSQL integration tests; Docker is required.
- Windows: use `mvnw.cmd`.

Integration tests run actual migrations, Spring transaction proxies, MyBatis SQL and HTTP
security in disposable PostgreSQL containers. Quote responses are deterministic. They
never target a shared or developer database. The obsolete disabled TDD roadmap was replaced
with executable tests and the BRD coverage matrix in `docs/db-backend-connection.md`.

`TradingIntegrationIT` covers acceptance vs execution, buys/sells, current/stale quotes,
limits, ownership, sessions, validation, persistent rejection, rollback and concurrency.
`PerpetualFuturesIT` covers perp fills at bid/ask, margin and fee debits, reduce-only closes,
idempotency, collateral and leverage rejections, ownership, once-per-hour funding,
liquidation and open interest; `Perp*Test` unit-tests the isolated-margin math.
`RestartRecoveryIT` closes/restarts actual application contexts against the same database.
`MigrationIT` verifies safe refusal of an unmanaged schema and preservation after explicit
V1-compatible adoption. Mocks alone do not prove database atomicity.

For frontend/browser commands, see the repository README and connection guide.
