# Spring trading API

[openapi.yaml](openapi.yaml) describes the Spring Boot API at `/api/v1`.
The checked-in Angular app in [Frontend/](C:/Users/Administrator/programming/Rocket-Trading/Frontend)
calls that same interface directly from `http://localhost:4200` to `http://localhost:8081/api/v1`.
The contract documents behavior; Spring controllers and validation enforce it.

Public endpoints: `POST /auth/register` and `POST /auth/sign-in`.
All other endpoints require the returned bearer token and an active server-side session.
Success uses `{data, meta}`; errors use `{error}`; sign-out returns 204.
Registration and sign-in remain email-only local fixtures.

## Trading dashboard support

The Angular trading dashboard currently uses:

- `GET /me` for the signed-in fixture client profile
- `GET /instruments` for the supported trading catalogue
- `GET /portfolio/summary` for cash and positions
- `GET /orders`, `GET /fills/{orderId}`, and `GET /orders/{orderId}/timeline`
  for blotter, fills, and audit reconstruction
- `GET /reporting/overview` for a lightweight internal reporting snapshot

## Orders

Send `POST /orders` with `Authorization: Bearer <token>` and a unique
`Idempotency-Key: <UUID>` header, plus this JSON:

```json
{"symbol":"AAPL","side":"BUY","quantity":1,"market":"stock","orderType":"MARKET"}
```

A successful submission commits `ACCEPTED` and returns 202. Execution happens later in
another transaction. Poll `GET /orders/{orderId}` or `GET /orders`; the Angular UI does
this every two seconds for pending orders. `GET /fills/{orderId}` returns execution prices
only to the order's owner. Unknown or other-client orders return 404.

Reuse the request key after a network/server error. Equivalent normalized payloads return
the same order at its current status; changed payloads return `409 IDEMPOTENCY_CONFLICT`.
Previously rejected orders return `409 ORDER_REJECTED` again for the same key.
Business rejections are committed to history even though submission returns 409.
Invalid input (400) and unknown instruments (404) do not create an order.

Quantity is required, positive, and limited to six fractional/twelve integer digits.
LIMIT requires a positive price with at most four fractional/twelve integer digits;
MARKET must omit the price or send null. Limit, balances, tradability and current quote
are rechecked at execution. LIMIT orders reject when the price is unsuitable; they do
not wait for a future market price. Partial fills and FX conversion are not implemented.

The client cannot select arbitrary new instruments. The migration seeds the supported
USD catalogue; database catalogue administration is outside this UI.

## Persistence and verification

The authoritative schema is in the backend's Flyway migrations. Repositories use MyBatis
SQL; Spring transactions own settlement. Do not also install the prototype SQL settlement
triggers. Read [the run/migration guide](../db-backend-connection.md) before using old data.

`./mvnw verify` in the backend module tests real PostgreSQL persistence, ownership, rejection,
rollback, retries, concurrency and restart recovery. Browser tests exercise the real API
and database using an HTTP quote fixture. Changes to routes, payloads or validation must
update this contract, Angular interfaces and relevant tests together.

Remaining limits include fixture authentication, large numeric client-ID display precision,
restricted markets and same-database reporting rather than Kafka-backed projection isolation.
See the guide's BRD matrix.
