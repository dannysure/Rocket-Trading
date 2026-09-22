# Rocket Trading Platform Testing Strategy

## Purpose

This document explains how to test the platform defined in [implementation-plan/plan.md](C:/Users/Administrator/Downloads/Rocket-Trading/implementation-plan/plan.md), how to organize those tests by architecture and phase, and how to keep the project TDD-driven.

## Testing principles

1. Write the failing test first.
2. Test business rules at the lowest useful layer.
3. Treat ledger correctness, auth, and auditability as highest-priority coverage.
4. Test failure paths, not only success paths.
5. Keep reporting-path tests separate from live trading-path tests.

## Test organization

### Frontend - Angular

Place tests close to the UI logic:

- component tests for auth, order ticket, holdings, blotter, insights,
- service tests for API calls and mapping,
- end-to-end tests for sign-in, quote lookup, order placement, and portfolio refresh.

Suggested organization:

```text
frontend/src/app/
|- features/
|- core/
`- testing/
```

### Gateway - NestJS

Use:

- unit tests for guards, auth/session logic, DTO validation, and route behavior,
- integration tests for gateway-to-service orchestration,
- API contract tests for request/response shape.

Suggested organization:

```text
backend/gateway/
|- src/
`- test/
```

### Trading core - Spring Boot

Use:

- unit tests for trading rules,
- service tests for order acceptance and portfolio updates,
- integration tests for transaction boundaries,
- scenario tests for submitted -> accepted -> filled/rejected flows.

Suggested organization:

```text
backend/trading-core/src/
|- main/
`- test/
```

### Reporting service - Spring Boot

Use:

- unit tests for aggregations and insight calculations,
- consumer/projection tests for event ingestion,
- isolation tests to prove reporting does not impact live trading behavior.

### Database, Kafka, and Redis

Use:

- migration tests,
- transactional integrity tests,
- event publication and replay tests,
- session revocation and cache safety tests.

## When tests should be created

### Before implementation

Create first:

- unit tests for business rules,
- controller/route tests for public behavior,
- component tests for expected UI behavior.

### As soon as interfaces are defined

Create:

- API contract tests,
- event schema tests,
- integration tests for service boundaries.

### Before a phase is marked complete

Create:

- regression tests for that phase,
- negative-path tests,
- restart/retry/duplicate-message coverage where relevant.

## TDD workflow

For each story or task:

1. define the behavior,
2. write a failing test,
3. implement the smallest passing code,
4. refactor safely,
5. run focused tests,
6. run phase-level regression before merge.

## Phase-by-phase testing plan

### Phase 1 - Platform foundation

Test:

- app bootstraps,
- routes start,
- basic builds succeed,
- initial contracts are stable.

Create these tests during Phase 1:

- Angular app smoke test,
- NestJS boot and route smoke tests,
- Spring Boot context tests,
- initial migration/config tests.

### Phase 2 - Identity and access

Test:

- registration,
- sign-in,
- invalid credentials,
- session expiry,
- session revocation,
- client data isolation.

Create these tests before auth code is considered done.

### Phase 3 - Market data and pricing

Test:

- quote ingestion/parsing,
- supported asset classes,
- indicative pricing output,
- stale/unavailable quote behavior.

Create adapter and quote tests before wiring real providers.

### Phase 4 - Order placement and execution

Test:

- buy and sell validation,
- accepted-order persistence,
- execution against current quote,
- atomic holdings/cash/trade updates,
- rejection scenarios.

Create these tests before implementing live order mutation logic.

### Phase 5 - Portfolio and client experience

Test:

- holdings and cash rendering,
- chronological blotter/history,
- live status refresh,
- client journey from sign-in to trade to portfolio update.

Create component and e2e tests before phase sign-off.

### Phase 6 - Audit and reporting

Test:

- immutable audit storage,
- lifecycle reconstruction,
- reporting aggregations,
- reporting isolation from live trading.

Create audit and projection tests before exposing internal dashboards.

### Phase 7 - Differentiation feature

Test:

- watchlist CRUD,
- alert triggers,
- alert delivery to correct client only.

Create these tests before notification fan-out and UI completion.

### Phase 8 - Hardening and release readiness

Test:

- regression across all BR-mapped flows,
- resilience,
- restart recovery,
- duplicate-message handling,
- unauthorized access attempts.

Create these tests continuously, but finish the full suite before release approval.

## Minimum required suites

| Area | Minimum automated coverage |
|------|----------------------------|
| Auth | valid login, invalid login, expired/revoked session |
| Access control | client A cannot access client B data |
| Orders | accept/reject rules for cash, holdings, tradability |
| Ledger | holdings, cash, and trade record update together |
| Quotes | current/indicative quote retrieval and invalid data handling |
| Audit | full trade lifecycle reconstruction |
| Reporting | event-driven projections and non-interference with live trading |
| UI | sign-in, quote view, place order, updated portfolio and history |

## Team process to ensure TDD

### Pull requests

Each PR should include:

- the new or changed tests for the behavior,
- the implementation that makes them pass,
- focused scope tied to one story or vertical slice.

### Code review checklist

Reviewers should ask:

1. What failing test defined this change first?
2. Are both happy-path and failure-path behaviors covered?
3. Is the test at the right layer?
4. Does the change protect data correctness and client isolation?

### CI gates

Run at minimum:

- frontend tests,
- gateway tests,
- trading-core tests,
- reporting-service tests,
- migration/config tests,
- selected critical-path e2e tests.

## Final guidance

If the team follows three rules, use these:

1. write tests before production code,
2. protect auth, ledger correctness, and auditability first,
3. never close a phase until its focused regression suite exists and passes.