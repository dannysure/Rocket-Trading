package com.rockettrading.rocket_trading.model;

import jakarta.persistence.Entity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DomainModelStructureTest {

    static Stream<Class<?>> constructibleDomainTypes() {
        return Stream.of(
                Order.class,
                Client.class,
                Position.class,
                BlotterEntry.class,
                AuditEvent.class,
                Watchlist.class,
                Session.class,
                Quote.class,
                PriceAlert.class,
                Instrument.class,
                Fill.class,
                CashAccount.class
        );
    }

    @ParameterizedTest(name = "{0} can be instantiated")
    @MethodSource("constructibleDomainTypes")
    @DisplayName("all starter domain objects are loadable")
    void allStarterDomainObjectsAreLoadable(Class<?> domainType) throws Exception {
        assertDoesNotThrow(() -> domainType.getDeclaredConstructor().newInstance());
        assertTrue(Modifier.isPublic(domainType.getModifiers()));
    }

    @Test
    @DisplayName("client and session are marked as JPA entities")
    void clientAndSessionAreMarkedAsJpaEntities() {
        assertTrue(Client.class.isAnnotationPresent(Entity.class));
        assertTrue(Session.class.isAnnotationPresent(Entity.class));
    }

    @Test
    @DisplayName("core order fields define the first TDD contract")
    void orderFieldsDefineTheFirstTddContract() {
        assertDeclaredFieldNames(Order.class, List.of("orderId", "side", "quantity", "status"));
    }

    @Test
    @DisplayName("client fields define the first identity contract")
    void clientFieldsDefineTheFirstIdentityContract() {
        assertDeclaredFieldNames(Client.class, List.of("clientId", "name", "email"));
    }

    @Test
    @DisplayName("market and portfolio models are available for later slices")
    void marketAndPortfolioModelsAreAvailableForLaterSlices() {
        assertDeclaredFieldNames(Position.class, List.of("symbol", "quantity", "averageCost", "currentPrice", "totalCostBasis"));
        assertDeclaredFieldNames(Quote.class, List.of("symbol", "bid", "ask", "price", "bidVolume", "askVolume", "capturedAt"));
        assertDeclaredFieldNames(Instrument.class, List.of("symbol", "assetClass", "tradable"));
    }

    private static void assertDeclaredFieldNames(Class<?> type, List<String> expectedFieldNames) {
        Field[] declaredFields = type.getDeclaredFields();
        assertEquals(expectedFieldNames.size(), declaredFields.length, "Unexpected field count for " + type.getSimpleName());

        for (String fieldName : expectedFieldNames) {
            assertDoesNotThrow(() -> type.getDeclaredField(fieldName));
        }
    }
}
