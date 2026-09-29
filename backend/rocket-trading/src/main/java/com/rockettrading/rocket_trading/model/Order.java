package com.rockettrading.rocket_trading.model;

import java.time.Instant;

public class Order {
    public Order() {
        this.status = "DRAFT";
    }

    private long orderId;
    private String side;
    private long quantity;
    private String status;

    public Order(long orderId, String side, long quantity, String status) {
        setOrderId(orderId);
        setSide(side);
        setQuantity(quantity);
        setStatus(status);
    }

    public Transition submit() {
        ensureOrderCanBeSubmitted();
        return transitionTo("SUBMIT", "DRAFT", "SUBMITTED");
    }

    public Transition submitOrder() {
        return submit();
    }

    public Transition accept() {
        return transitionTo("ACCEPT", "SUBMITTED", "ACCEPTED");
    }

    public Transition acceptOrder() {
        return accept();
    }

    public Transition reject() {
        String currentStatus = currentStatus();
        if (!"SUBMITTED".equals(currentStatus) && !"ACCEPTED".equals(currentStatus)) {
            throw new IllegalStateException("Only submitted or accepted orders can be rejected");
        }

        return transition(currentStatus, "REJECT", "REJECTED");
    }

    public Transition rejectOrder() {
        return reject();
    }

    public Transition fill() {
        return transitionTo("FILL", "ACCEPTED", "FILLED");
    }

    public Transition fillOrder() {
        return fill();
    }

    public long getOrderId() {
        return orderId;
    }

    public void setOrderId(long orderId) {
        if (orderId <= 0) {
            throw new IllegalArgumentException("orderId must be positive");
        }
        this.orderId = orderId;
    }

    public String getSide() {
        return side;
    }

    public void setSide(String side) {
        if (side == null) {
            this.side = null;
            return;
        }

        String normalizedSide = side.trim().toUpperCase();
        if (!"BUY".equals(normalizedSide) && !"SELL".equals(normalizedSide)) {
            throw new IllegalArgumentException("side must be BUY or SELL");
        }

        this.side = normalizedSide;
    }

    public long getQuantity() {
        return quantity;
    }

    public void setQuantity(long quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        this.quantity = quantity;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            this.status = "DRAFT";
            return;
        }

        String normalizedStatus = status.trim().toUpperCase();
        if (!"DRAFT".equals(normalizedStatus)
                && !"SUBMITTED".equals(normalizedStatus)
                && !"ACCEPTED".equals(normalizedStatus)
                && !"REJECTED".equals(normalizedStatus)
                && !"FILLED".equals(normalizedStatus)) {
            throw new IllegalArgumentException("status must be DRAFT, SUBMITTED, ACCEPTED, REJECTED, or FILLED");
        }

        this.status = normalizedStatus;
    }

    private void ensureOrderCanBeSubmitted() {
        if (orderId <= 0) {
            throw new IllegalStateException("Only fully defined draft orders can be submitted");
        }

        if (side == null || quantity <= 0) {
            throw new IllegalStateException("Only fully defined draft orders can be submitted");
        }
    }

    private Transition transitionTo(String action, String expectedCurrentStatus, String nextStatus) {
        String currentStatus = currentStatus();
        if (!expectedCurrentStatus.equals(currentStatus)) {
            throw new IllegalStateException("Only " + expectedCurrentStatus.toLowerCase() + " orders can be " + action.toLowerCase() + "ed");
        }

        return transition(currentStatus, action, nextStatus);
    }

    private Transition transition(String previousStatus, String action, String nextStatus) {
        this.status = nextStatus;
        return new Transition(orderId, action, previousStatus, nextStatus, Instant.now());
    }

    private String currentStatus() {
        return status == null ? "DRAFT" : status;
    }

    public static final class Transition {
        private final long orderId;
        private final String action;
        private final String previousStatus;
        private final String currentStatus;
        private final Instant changedAt;

        public Transition(long orderId, String action, String previousStatus, String currentStatus, Instant changedAt) {
            this.orderId = orderId;
            this.action = action;
            this.previousStatus = previousStatus;
            this.currentStatus = currentStatus;
            this.changedAt = changedAt;
        }

        public long getOrderId() {
            return orderId;
        }

        public String getAction() {
            return action;
        }

        public String getPreviousStatus() {
            return previousStatus;
        }

        public String getCurrentStatus() {
            return currentStatus;
        }

        public Instant getChangedAt() {
            return changedAt;
        }
    }
}
