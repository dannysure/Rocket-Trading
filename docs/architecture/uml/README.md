# UML Diagrams

These Mermaid source files capture the original proposed architecture, not the current runtime. The gateway, event bus, reporting projections and push updates in these diagrams are not implemented by the core connection phase. The current runtime is Angular → one Spring Boot application (including a scheduled order worker) → PostgreSQL, with browser polling. See [the current connection guide](../../db-backend-connection.md). The diagrams remain historical planning material. They live in the shared `docs/architecture/uml/` area because they span the browser, gateway, trading core, reporting, and database concerns rather than belonging to only one service.

## Class diagrams

- [class/trading-domain-model.mmd](class/trading-domain-model.mmd): core trading entities, lifecycle records, and the recommended watchlist capability.
- [class/platform-service-responsibilities.mmd](class/platform-service-responsibilities.mmd): high-level application services and their main dependencies.

## Sequence diagrams

- [sequence/order-placement-and-execution.mmd](sequence/order-placement-and-execution.mmd): end-to-end order flow from indicative pricing through execution and live status updates.
- [sequence/reporting-and-traceability.mmd](sequence/reporting-and-traceability.mmd): asynchronous reporting projection flow and audit reconstruction path.
