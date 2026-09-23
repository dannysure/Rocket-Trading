package com.rockettrading.rocket_trading;

import com.rockettrading.rocket_trading.model.Order;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderServiceTests {

    @Test
    @DisplayName("submit moves a draft order to submitted")
    void submitMovesDraftOrderToSubmitted() {
        Order order = new Order(101L, "buy", 50L, null);

        Order.Transition transition = order.submit();

        assertAll(
                () -> assertEquals(101L, transition.getOrderId()),
                () -> assertEquals("SUBMIT", transition.getAction()),
                () -> assertEquals("DRAFT", transition.getPreviousStatus()),
                () -> assertEquals("SUBMITTED", transition.getCurrentStatus()),
                () -> assertEquals("SUBMITTED", order.getStatus()),
                () -> assertNotNull(transition.getChangedAt())
        );
    }

    @Test
    @DisplayName("accept moves a submitted order to accepted")
    void acceptMovesSubmittedOrderToAccepted() {
        Order order = new Order(101L, "BUY", 50L, "SUBMITTED");

        Order.Transition transition = order.accept();

        assertAll(
                () -> assertEquals("ACCEPT", transition.getAction()),
                () -> assertEquals("SUBMITTED", transition.getPreviousStatus()),
                () -> assertEquals("ACCEPTED", transition.getCurrentStatus()),
                () -> assertEquals("ACCEPTED", order.getStatus())
        );
    }

    @Test
    @DisplayName("reject supports orders that were already accepted")
    void rejectSupportsAcceptedOrders() {
        Order order = new Order(101L, "SELL", 25L, "ACCEPTED");

        Order.Transition transition = order.reject();

        assertAll(
                () -> assertEquals("REJECT", transition.getAction()),
                () -> assertEquals("ACCEPTED", transition.getPreviousStatus()),
                () -> assertEquals("REJECTED", transition.getCurrentStatus()),
                () -> assertEquals("REJECTED", order.getStatus())
        );
    }

    @Test
    @DisplayName("fill moves an accepted order to filled")
    void fillMovesAcceptedOrderToFilled() {
        Order order = new Order(101L, "BUY", 25L, "ACCEPTED");

        Order.Transition transition = order.fill();

        assertAll(
                () -> assertEquals("FILL", transition.getAction()),
                () -> assertEquals("ACCEPTED", transition.getPreviousStatus()),
                () -> assertEquals("FILLED", transition.getCurrentStatus()),
                () -> assertEquals("FILLED", order.getStatus()),
                () -> assertNotNull(transition.getChangedAt())
        );
    }

    @Test
    @DisplayName("submit rejects incomplete draft orders")
    void submitRejectsIncompleteDraftOrders() {
        Order order = new Order();
        order.setSide("BUY");
        order.setStatus("DRAFT");

        IllegalStateException exception = assertThrows(IllegalStateException.class, order::submit);

        assertEquals("Only fully defined draft orders can be submitted", exception.getMessage());
    }

    @Test
    @DisplayName("fill rejects orders that were never accepted")
    void fillRejectsOrdersThatWereNeverAccepted() {
        Order order = new Order(101L, "BUY", 25L, "SUBMITTED");

        IllegalStateException exception = assertThrows(IllegalStateException.class, order::fill);

        assertEquals("Only accepted orders can be filled", exception.getMessage());
    }

    // Next useful slices: partial fills, cancel/amend rules, and audit/blotter side effects.
}
