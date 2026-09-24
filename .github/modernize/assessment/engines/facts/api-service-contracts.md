# API & Service Communication Contracts

This repository currently contains a single Spring Boot backend and domain model, but no implemented HTTP controllers yet. The contract below is therefore a contract-first proposal derived from the business specification and existing trading model classes, with a single-service synchronous request path plus server-push status updates for order lifecycle changes.

## Service Catalog

| Service | Port | Category | Purpose |
| --- | --- | --- | --- |
| rocket-trading | 8080 (default Spring Boot; not explicitly configured) | API Layer + Business | Client-facing trading API for registration, sign-in, order submission, pricing, portfolio views, blotter history, and audit-safe trade processing |

Frameworks in use for this service: Spring Boot Web, Spring Data JPA, PostgreSQL driver, Lombok, JUnit.

## API Endpoints Inventory

Versioning scheme: URL versioning via `/api/v1`.

| Service | Method | Path | Request Type | Response Type |
| --- | --- | --- | --- | --- |
| rocket-trading | POST | `/api/v1/auth/register` | `RegisterClientRequest` | `201 Created` -> `ApiResponse<ClientRegistrationResponse>`; errors: `400`, `409` |
| rocket-trading | POST | `/api/v1/auth/sign-in` | `SignInRequest` | `200 OK` -> `ApiResponse<SessionResponse>`; errors: `401` |
| rocket-trading | POST | `/api/v1/auth/sign-out` | bearer token only | `204 No Content`; errors: `401` |
| rocket-trading | GET | `/api/v1/portfolio/summary` | bearer token only | `200 OK` -> `ApiResponse<PortfolioSummaryResponse>`; errors: `401` |
| rocket-trading | GET | `/api/v1/positions` | bearer token only | `200 OK` -> `ApiResponse<List<PositionResponse>>`; errors: `401` |
| rocket-trading | GET | `/api/v1/cash-account` | bearer token only | `200 OK` -> `ApiResponse<CashAccountResponse>`; errors: `401` |
| rocket-trading | GET | `/api/v1/orders` | query params: `status?`, `from?`, `to?`, `limit?` | `200 OK` -> `ApiResponse<List<OrderResponse>>`; errors: `400`, `401` |
| rocket-trading | GET | `/api/v1/orders/{orderId}` | path param `orderId` | `200 OK` -> `ApiResponse<OrderResponse>`; errors: `401`, `404` |
| rocket-trading | POST | `/api/v1/orders` | `SubmitOrderRequest` | `202 Accepted` -> `ApiResponse<OrderResponse>` with initial `SUBMITTED` status; errors: `400`, `401`, `409`, `422` |
| rocket-trading | GET | `/api/v1/orders/stream` | bearer token only (SSE) | `200 OK` -> stream of `OrderStatusEvent` messages; errors: `401` |
| rocket-trading | GET | `/api/v1/quotes/{symbol}` | path param `symbol`; query param `side?`, `quantity?` | `200 OK` -> `ApiResponse<QuoteResponse>`; errors: `400`, `404`, `409` |
| rocket-trading | GET | `/api/v1/blotter` | bearer token only; query params: `from?`, `to?`, `limit?` | `200 OK` -> `ApiResponse<List<BlotterEntryResponse>>`; errors: `400`, `401` |
| rocket-trading | GET | `/api/v1/fills/{orderId}` | path param `orderId` | `200 OK` -> `ApiResponse<List<FillResponse>>`; errors: `401`, `404` |
| rocket-trading | GET | `/api/v1/watchlists` | bearer token only | `200 OK` -> `ApiResponse<List<WatchlistResponse>>`; errors: `401` |
| rocket-trading | POST | `/api/v1/watchlists` | `CreateWatchlistRequest` | `201 Created` -> `ApiResponse<WatchlistResponse>`; errors: `400`, `401` |
| rocket-trading | POST | `/api/v1/watchlists/{watchlistId}/instruments` | `WatchlistInstrumentRequest` | `200 OK` -> `ApiResponse<WatchlistResponse>`; errors: `400`, `401`, `404`, `409` |
| rocket-trading | DELETE | `/api/v1/watchlists/{watchlistId}/instruments/{symbol}` | path params `watchlistId`, `symbol` | `204 No Content`; errors: `401`, `404` |
| rocket-trading | GET | `/api/v1/price-alerts` | bearer token only | `200 OK` -> `ApiResponse<List<PriceAlertResponse>>`; errors: `401` |
| rocket-trading | POST | `/api/v1/price-alerts` | `CreatePriceAlertRequest` | `201 Created` -> `ApiResponse<PriceAlertResponse>`; errors: `400`, `401`, `409` |
| rocket-trading | POST | `/api/v1/price-alerts/{alertId}/dismiss` | path param `alertId` | `200 OK` -> `ApiResponse<PriceAlertResponse>`; errors: `401`, `404`, `409` |

Response envelope for successful calls:

```json
{
  "data": {},
  "meta": {
    "requestId": "6f1f6d28-7fe5-4b7f-9af7-e7a6494f5cb1",
    "timestamp": "2026-09-24T14:00:00Z"
  }
}
```

Error envelope for every non-2xx response:

```json
{
  "error": {
    "code": "INSUFFICIENT_CASH",
    "message": "Available balance cannot cover the requested order",
    "details": [
      {
        "field": "quantity",
        "issue": "must be positive"
      }
    ],
    "requestId": "6f1f6d28-7fe5-4b7f-9af7-e7a6494f5cb1",
    "timestamp": "2026-09-24T14:00:00Z"
  }
}
```

Recommended error codes for the current domain rules: `VALIDATION_FAILED`, `UNAUTHENTICATED`, `FORBIDDEN`, `NOT_FOUND`, `ORDER_STATE_INVALID`, `INSUFFICIENT_CASH`, `INSUFFICIENT_HOLDING`, `INSTRUMENT_NOT_TRADABLE`, `QUOTE_STALE`, `PRICE_UNAVAILABLE`, `CONFLICT`, `INTERNAL_ERROR`.

## Management & Observability Endpoints

| Service | Endpoint | Custom Metrics (if any) |
| --- | --- | --- |
| rocket-trading | None currently implemented. Recommended next additions: `/actuator/health`, `/actuator/info`, `/actuator/metrics` once Actuator is added. | None currently implemented |

## DTOs & Contracts

No dedicated API DTO package exists yet in the codebase; the current `model` package contains service-level domain entities such as `Client`, `Session`, `Order`, `Position`, `CashAccount`, `Quote`, `Fill`, `BlotterEntry`, `AuditEvent`, `Instrument`, `Watchlist`, and `PriceAlert`. Those should remain internal service models rather than becoming the wire contract directly.

Recommended contract-first DTOs:

- Request DTOs: `RegisterClientRequest`, `SignInRequest`, `SubmitOrderRequest`, `CreateWatchlistRequest`, `WatchlistInstrumentRequest`, `CreatePriceAlertRequest`
- Response DTOs: `ClientRegistrationResponse`, `SessionResponse`, `PortfolioSummaryResponse`, `PositionResponse`, `CashAccountResponse`, `OrderResponse`, `QuoteResponse`, `FillResponse`, `BlotterEntryResponse`, `WatchlistResponse`, `PriceAlertResponse`, `OrderStatusEvent`
- Envelope DTOs: `ApiResponse<T>`, `ErrorResponse`, `ErrorDetail`

Gateway-level DTOs are not needed yet because the current design is a single backend service rather than a multi-service gateway. If reporting is later split out, keep read models for dashboards separate from trading write models.

Immutability recommendation:

- Use Java records for all request/response DTOs.
- Keep domain state transitions inside services and domain models.
- Existing immutable contract-like objects already present in code: `Client.Registration`, `Client.SignIn`, and `Order.Transition`.

Serialization and contract publication:

- JSON serialization would use Spring MVC's default Jackson mapper.
- No OpenAPI document, Swagger annotations, GraphQL schema, or protobuf schema is present yet.
- Recommended next artifact: publish `openapi.yaml` from this contract before implementing controllers.

## Communication Patterns

Current implementation posture:

- Single deployable service only; no gateway, no service discovery, and no asynchronous broker.
- No HTTP clients, retry libraries, circuit breakers, or explicit timeout policies are configured in the codebase today.
- No authentication, authorization, TLS, or Spring Security configuration is implemented yet; if controllers were added as-is, endpoints would be publicly accessible.

Recommended sprint decision for authentication:

- Use the Section 10 sign-in fixture and implement a stubbed authentication path this sprint.
- Concretely: `POST /api/v1/auth/sign-in` accepts fixture-backed credentials and returns a short-lived bearer token or JWT-shaped token containing `clientId`, `sessionId`, and `expiresAt`.
- Validate only token signature/shape, expiry, and client ownership checks this sprint; backlog real credential storage and production identity integration as separate work.

BR-09 atomicity must be enforced in the application service layer, not assumed:

- Introduce an `OrderExecutionService.fillAcceptedOrder(...)` method annotated with `@Transactional`.
- Inside that one transaction: lock/load the accepted order, quote decision, cash account, and position; create the `Fill`; settle or release cash; apply or reduce the position; append the blotter record; append the audit record; and update the order status.
- If any write fails, the transaction rolls back and none of `Fill`, `CashAccount`, `Position`, `BlotterEntry`, or order status changes become visible.
- Emit the `OrderStatusEvent` for BR-07 only after commit so the UI never sees a status that later rolls back.

Suggested synchronous trading path:

1. Authenticate and resolve `clientId` from bearer token.
2. Persist order intent with `SUBMITTED`.
3. Validate trading rules.
4. Mark accepted order as `ACCEPTED`.
5. Price against a current quote.
6. Execute fill in one database transaction.
7. Publish post-commit status event to SSE subscribers.

Failure mapping that the API should expose:

- Validation failure -> `400 Bad Request`
- Missing or expired session -> `401 Unauthorized`
- Access to another client's resource -> `403 Forbidden`
- Order rejected by business rules -> `409 Conflict` with domain error code
- Stale or unavailable quote -> `409 Conflict`
- Unexpected processing failure before commit -> `500 Internal Server Error`

Startup dependency chain: the service currently depends only on its own database configuration for availability. No external broker or discovery system is present.

## Service Technology Matrix

| Service | Web | Data Access | Discovery | Gateway | Actuator | Cache | Metrics |
| --- | --- | --- | --- | --- | --- | --- | --- |
| rocket-trading | Spring MVC (`spring-boot-starter-web`) | Spring Data JPA + PostgreSQL driver | none | none | none currently | none | none currently |

## Service Communication Sequence

<!-- mermaid-checked: every participant uses `participant Id as "Label"`, no \n in aliases/messages/notes, every alt/opt/loop closed by end, no `:` inside any alias -->
~~~mermaid
sequenceDiagram
    participant Client as "Client"
    participant OrdersApi as "Orders API"
    participant AuthSvc as "Session Service"
    participant OrderSvc as "Order Execution Service"
    participant QuoteSvc as "Quote Service"
    participant TxStore as "Transactional Store"
    participant Stream as "Order Status Stream"

    Client->>OrdersApi: POST /api/v1/orders
    OrdersApi->>AuthSvc: validate bearer token
    alt Session invalid or expired
        AuthSvc-->>OrdersApi: authentication failed
        OrdersApi-->>Client: 401 ErrorResponse
    else Session valid
        AuthSvc-->>OrdersApi: clientId and sessionId
        OrdersApi->>OrderSvc: submitOrder(request, clientId)
        OrderSvc->>TxStore: save order status SUBMITTED
        OrderSvc->>TxStore: validate cash holdings and tradable state
        alt Validation failed
            OrderSvc->>TxStore: save rejected order and audit
            OrderSvc-->>OrdersApi: OrderResponse(REJECTED)
            OrdersApi-->>Client: 409 ErrorResponse
        else Validation passed
            OrderSvc->>TxStore: update order status ACCEPTED
            OrderSvc->>QuoteSvc: get current quote
            alt Quote unavailable or stale
                QuoteSvc-->>OrderSvc: quote unavailable
                OrderSvc->>TxStore: save rejection audit
                OrderSvc-->>OrdersApi: OrderResponse(REJECTED)
                OrdersApi-->>Client: 409 ErrorResponse
            else Quote available
                QuoteSvc-->>OrderSvc: QuoteResponse
                critical Fill settlement transaction
                    OrderSvc->>TxStore: create fill record
                    OrderSvc->>TxStore: update cash account
                    OrderSvc->>TxStore: update position
                    OrderSvc->>TxStore: append blotter and audit
                    OrderSvc->>TxStore: update order status FILLED
                end
                OrderSvc-)Stream: publish OrderStatusEvent after commit
                OrderSvc-->>OrdersApi: OrderResponse(FILLED)
                OrdersApi-->>Client: 202 ApiResponse<OrderResponse>
            end
        end
    end
~~~
