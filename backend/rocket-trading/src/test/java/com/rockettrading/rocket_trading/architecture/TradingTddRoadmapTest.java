package com.rockettrading.rocket_trading.architecture;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TradingTddRoadmapTest {

    @Nested
    @DisplayName("identity and access")
    class IdentityAndAccess {

        @Test
        @Disabled("Next TDD slice: registration and secure sign-in")
        @DisplayName("registers and signs in a client")
        void registersAndSignsInAClient() {
        }

        @Test
        @Disabled("Next TDD slice: session expiry and revocation")
        @DisplayName("expires and revokes sessions")
        void expiresAndRevokesSessions() {
        }
    }

    @Nested
    @DisplayName("orders and execution")
    class OrdersAndExecution {

        @Test
        @Disabled("Next TDD slice: order validation against cash, holdings, and tradability")
        @DisplayName("validates orders before acceptance")
        void validatesOrdersBeforeAcceptance() {
        }

        @Test
        @Disabled("Next TDD slice: accepted order persistence before execution")
        @DisplayName("persists accepted orders before execution")
        void persistsAcceptedOrdersBeforeExecution() {
        }

        @Test
        @Disabled("Next TDD slice: fill updates holdings, cash, and trade record together")
        @DisplayName("updates the ledger atomically on fill")
        void updatesTheLedgerAtomicallyOnFill() {
        }
    }

    @Nested
    @DisplayName("portfolio and history")
    class PortfolioAndHistory {

        @Test
        @Disabled("Next TDD slice: client-scoped holdings, cash, and blotter views")
        @DisplayName("shows only the signed-in client's data")
        void showsOnlyTheSignedInClientsData() {
        }

        @Test
        @Disabled("Next TDD slice: live status refresh")
        @DisplayName("refreshes order status without page reload")
        void refreshesOrderStatusWithoutPageReload() {
        }
    }

    @Nested
    @DisplayName("audit and reporting")
    class AuditAndReporting {

        @Test
        @Disabled("Next TDD slice: immutable audit trail")
        @DisplayName("records an immutable audit trail")
        void recordsAnImmutableAuditTrail() {
        }

        @Test
        @Disabled("Next TDD slice: reporting projections isolated from live trading")
        @DisplayName("builds reporting projections off asynchronous events")
        void buildsReportingProjectionsOffAsynchronousEvents() {
        }
    }
}
