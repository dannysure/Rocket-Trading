# Rocket Trading Platform Implementation Plan

## Purpose

This plan translates the business requirements in [Project-Business-Requirements-Specification.txt](C:/Users/Administrator/Downloads/Rocket-Trading/Project-Business-Requirements-Specification.txt) into a practical implementation roadmap for a full-stack trading platform built with Angular, NestJS, Spring Boot, PostgreSQL, Apache Kafka, and Redis.

## Business goals covered

The platform must enable:

- secure client registration and sign-in,
- client-only access to positions, cash, and history,
- order submission, validation, pricing, and execution,
- correct holdings and cash updates,
- permanent auditability,
- internal reporting isolated from the live trading path,
- one differentiated feature beyond core trading.

## Recommended architecture

### Frontend - Angular

Angular will provide:

- registration and sign-in flows,
- order ticket and indicative pricing,
- holdings, cash, and blotter screens,
- real-time order status updates,
- internal reporting dashboards for authorized users.

### API gateway - NestJS

NestJS should act as the client-facing gateway:

- auth/session entry point,
- request validation and access control,
- aggregation layer for frontend APIs,
- WebSocket or SSE channel for near-real-time updates,
- isolation between browser clients and backend domain services.

### Trading domain - Spring Boot

Spring Boot should own:

- core trading rules,
- order lifecycle orchestration,
- quote usage at execution time,
- holdings and cash mutation logic,
- audit event generation,
- durable domain workflows.

### Data and messaging

- **PostgreSQL**: system of record for clients, orders, fills, holdings, cash, audit events, and reporting projections.
- **Kafka**: event backbone for order lifecycle, portfolio updates, audit events, and reporting ingestion.
- **Redis**: session revocation, quote caching, throttling, and live update support.

## Core architectural rules

1. Accepted orders must be recorded before execution starts.
2. Holdings, cash, and trade records must update atomically.
3. Reporting must consume asynchronous events, not query the live write path directly.
4. No client may access another client's data.
5. Audit records must survive restart, retry, and deployment failures.

## Proposed phase plan

### Phase 1 - Platform foundation

- Set up [frontend/](C:/Users/Administrator/Downloads/Rocket-Trading/frontend), [backend/gateway/](C:/Users/Administrator/Downloads/Rocket-Trading/backend/gateway), [backend/trading-core/](C:/Users/Administrator/Downloads/Rocket-Trading/backend/trading-core), and [backend/reporting-service/](C:/Users/Administrator/Downloads/Rocket-Trading/backend/reporting-service).
- Define base API contracts and event shapes.
- Establish local development and CI entry points.
- Align structure with the team branch strategy.

### Phase 2 - Identity and access

- Build registration and secure sign-in.
- Add session expiry and revocation.
- Enforce client-scoped authorization for all portfolio and history access.

### Phase 3 - Market data and pricing

- Integrate supported launch asset classes:
  - equities (UK/US/India),
  - FX,
  - crypto.
- Provide indicative quotes before order submission.
- Cache current quotes safely in Redis.

### Phase 4 - Order placement and execution

- Accept buy and sell orders.
- Validate trading rules before acceptance.
- Record accepted intent durably.
- Execute against the current quote.
- Emit lifecycle updates for submitted, accepted, filled, and rejected states.

### Phase 5 - Portfolio and client experience

- Expose holdings, cash balance, and order history.
- Add live blotter/status refresh without manual page reload.
- Keep the UX simple for non-professional investors.

### Phase 6 - Audit and reporting

- Persist an immutable audit trail.
- Support full trade reconstruction.
- Build reporting projections and internal dashboards without affecting live trading.

### Phase 7 - Differentiation feature

Recommended additional feature for BR-18:

- **watchlists and price alerts**

Reason:

- high client value,
- low operational risk compared with margin/leverage,
- reuses the market data pipeline already needed for trading.

### Phase 8 - Hardening and release readiness

- resilience testing,
- duplicate-message protection,
- restart recovery,
- security checks,
- operational dashboards and support tooling.

## Suggested project structure

```text
Rocket-Trading/
|- frontend/
|- backend/
|  |- gateway/
|  |- trading-core/
|  `- reporting-service/
|- database/
|- implementation-plan/
|  `- plan.md
`- testing-strategy/
   `- testing-strategy.md
```

## Requirement mapping

| BRS Ref | Requirement Summary | Planned phase coverage |
|--------|----------------------|------------------------|
| BR-01 | Register and sign in securely | Phases 1, 2 |
| BR-02 | Clients only access their own data | Phases 2, 5, 8 |
| BR-03 | Time-limited and revocable sessions | Phases 2, 8 |
| BR-04 | Submit buy/sell orders | Phases 4, 5 |
| BR-05 | Validate every order | Phase 4 |
| BR-06 | Record accepted order before execution | Phase 4 |
| BR-07 | Live order status updates | Phase 5 |
| BR-08 | Execute against current quote | Phases 3, 4 |
| BR-09 | Update holdings, cash, and trade record together | Phase 4 |
| BR-10 | Show current holdings and cash | Phase 5 |
| BR-11 | Show own order/fill history | Phase 5 |
| BR-12 | Support equities, FX, crypto pricing | Phase 3 |
| BR-13 | Show indicative price before order | Phases 3, 5 |
| BR-14 | Permanent record of accepted orders and outcomes | Phases 4, 6 |
| BR-15 | Reconstruct full trade lifecycle | Phase 6 |
| BR-16 | Reporting independent of live trading | Phases 1, 6, 8 |
| BR-17 | Surface business insights | Phase 6 |
| BR-18 | Add one extra valuable capability | Phase 7 |

## Delivery risks to manage

- market data provider inconsistency across asset classes,
- incorrect balance updates during partial failure,
- reporting load affecting live trading,
- audit gaps from missing or duplicated lifecycle events,
- accidental cross-team conflicts with the database workstream.

## Immediate next delivery slices

1. scaffold and stabilize the project structure,
2. complete auth and access control,
3. define database contracts with the database engineer,
4. implement the order lifecycle vertically,
5. add reporting projections after the trade flow is stable.