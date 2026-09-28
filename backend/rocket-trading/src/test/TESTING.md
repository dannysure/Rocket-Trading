# Rocket Trading backend TDD starter map

This module is set up for a simple Maven + JUnit workflow.

Run the current test suite from this module root with:

```bat
mvn test
```

## Current starter tests

- `RocketTradingApplicationTests` - lightweight boot annotation smoke test
- `controller/UserControllerTest` - controller skeleton smoke test
- `service/UserServiceTest` - service skeleton smoke test
- `repository/UserRepositoryTest` - repository skeleton smoke test
- `model/DomainModelStructureTest` - starter domain object contract checks
- `architecture/TradingTddRoadmapTest` - disabled roadmap for the next TDD slices

## Recommended next test slices

### 1. Identity and access
- register client
- sign in client
- reject invalid credentials
- expire / revoke a session

### 2. Order placement
- reject invalid side or quantity
- reject unsupported instrument
- reject orders with insufficient cash or holdings
- accept valid orders and persist intent first

### 3. Portfolio and history
- show only the signed-in client’s holdings
- show cash balance
- show chronological blotter history

### 4. Audit and reporting
- write audit events for accepted / rejected / filled actions
- verify trades can be reconstructed later
- keep reporting reads separate from trading writes

## Suggested rule for the next implementation slice

Write one failing test for one business rule, implement the smallest change needed to pass it, then refactor.


