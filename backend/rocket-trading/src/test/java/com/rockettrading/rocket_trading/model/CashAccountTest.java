package com.rockettrading.rocket_trading.model;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CashAccountTest {
    private CashAccount account;

    @BeforeEach
    void setUp() {
        account = new CashAccount(new BigDecimal("10000.00"));
    }

    @Test
    void testNoArgConstructor() {
        CashAccount emptyAccount = new CashAccount();
        assertEquals(BigDecimal.ZERO, emptyAccount.getAvailableBalance());
        assertEquals(BigDecimal.ZERO, emptyAccount.getReservedBalance());
        assertEquals(BigDecimal.ZERO, emptyAccount.getTotalBalance());
    }

    @Test
    void testFullConstructor() {
        assertEquals(new BigDecimal("10000.00"), account.getTotalBalance());
        assertEquals(new BigDecimal("10000.00"), account.getAvailableBalance());
        assertEquals(BigDecimal.ZERO, account.getReservedBalance());
    }

    @Test
    void testFullConstructorWithSmallAmount() {
        CashAccount smallAccount = new CashAccount(new BigDecimal("100.50"));
        assertEquals(new BigDecimal("100.50"), smallAccount.getTotalBalance());
        assertEquals(new BigDecimal("100.50"), smallAccount.getAvailableBalance());
    }

    @Test
    void testSetTotalBalance() {
        account.setTotalBalance(new BigDecimal("5000.00"));
        assertEquals(new BigDecimal("5000.00"), account.getTotalBalance());
    }

    @Test
    void testSetTotalBalanceRejectsNegative() {
        assertThrows(IllegalArgumentException.class, () -> 
            account.setTotalBalance(new BigDecimal("-100.00")));
    }

    @Test
    void testSetTotalBalanceRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> 
            account.setTotalBalance(null));
    }

    @Test
    void testSetAvailableBalance() {
        account.setAvailableBalance(new BigDecimal("8000.00"));
        assertEquals(new BigDecimal("8000.00"), account.getAvailableBalance());
    }

    @Test
    void testSetAvailableBalanceRejectsNegative() {
        assertThrows(IllegalArgumentException.class, () -> 
            account.setAvailableBalance(new BigDecimal("-500.00")));
    }

    @Test
    void testSetAvailableBalanceRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> 
            account.setAvailableBalance(null));
    }

    @Test
    void testSetReservedBalance() {
        account.setReservedBalance(new BigDecimal("2000.00"));
        assertEquals(new BigDecimal("2000.00"), account.getReservedBalance());
    }

    @Test
    void testSetReservedBalanceRejectsNegative() {
        assertThrows(IllegalArgumentException.class, () -> 
            account.setReservedBalance(new BigDecimal("-1000.00")));
    }

    @Test
    void testSetReservedBalanceRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> 
            account.setReservedBalance(null));
    }

    @Test
    void testReserveForOrder() {
        account.reserveForOrder(new BigDecimal("3000.00"));
        assertEquals(new BigDecimal("7000.00"), account.getAvailableBalance());
        assertEquals(new BigDecimal("3000.00"), account.getReservedBalance());
        assertEquals(new BigDecimal("10000.00"), account.getTotalBalance());
    }

    @Test
    void testReserveForOrderMultipleTimes() {
        account.reserveForOrder(new BigDecimal("2000.00"));
        account.reserveForOrder(new BigDecimal("3000.00"));
        assertEquals(new BigDecimal("5000.00"), account.getAvailableBalance());
        assertEquals(new BigDecimal("5000.00"), account.getReservedBalance());
    }

    @Test
    void testReserveForOrderFullBalance() {
        account.reserveForOrder(new BigDecimal("10000.00"));
        assertEquals(new BigDecimal("0.00"), account.getAvailableBalance());
        assertEquals(new BigDecimal("10000.00"), account.getReservedBalance());
    }

    @Test
    void testReserveForOrderRejectsExceeding() {
        assertThrows(IllegalArgumentException.class, () -> 
            account.reserveForOrder(new BigDecimal("10001.00")));
    }

    @Test
    void testReserveForOrderRejectsNegative() {
        assertThrows(IllegalArgumentException.class, () -> 
            account.reserveForOrder(new BigDecimal("-500.00")));
    }

    @Test
    void testReserveForOrderRejectsZero() {
        assertThrows(IllegalArgumentException.class, () -> 
            account.reserveForOrder(BigDecimal.ZERO));
    }

    @Test
    void testReserveForOrderRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> 
            account.reserveForOrder(null));
    }

    @Test
    void testSettleFill() {
        account.reserveForOrder(new BigDecimal("5000.00"));
        account.settleFill(new BigDecimal("5000.00"));
        
        assertEquals(new BigDecimal("5000.00"), account.getAvailableBalance());
        assertEquals(new BigDecimal("0.00"), account.getReservedBalance());
        assertEquals(new BigDecimal("5000.00"), account.getTotalBalance());
    }

    @Test
    void testSettleFillPartial() {
        account.reserveForOrder(new BigDecimal("5000.00"));
        account.settleFill(new BigDecimal("3000.00"));
        
        assertEquals(new BigDecimal("5000.00"), account.getAvailableBalance());
        assertEquals(new BigDecimal("2000.00"), account.getReservedBalance());
        assertEquals(new BigDecimal("7000.00"), account.getTotalBalance());
    }

    @Test
    void testSettleFillMultipleFills() {
        account.reserveForOrder(new BigDecimal("5000.00"));
        account.settleFill(new BigDecimal("2000.00"));
        account.settleFill(new BigDecimal("3000.00"));
        
        assertEquals(new BigDecimal("5000.00"), account.getAvailableBalance());
        assertEquals(new BigDecimal("0.00"), account.getReservedBalance());
        assertEquals(new BigDecimal("5000.00"), account.getTotalBalance());
    }

    @Test
    void testSettleFillRejectsExceeding() {
        account.reserveForOrder(new BigDecimal("3000.00"));
        assertThrows(IllegalArgumentException.class, () -> 
            account.settleFill(new BigDecimal("3001.00")));
    }

    @Test
    void testSettleFillRejectsNegative() {
        account.reserveForOrder(new BigDecimal("2000.00"));
        assertThrows(IllegalArgumentException.class, () -> 
            account.settleFill(new BigDecimal("-500.00")));
    }

    @Test
    void testSettleFillRejectsZero() {
        account.reserveForOrder(new BigDecimal("2000.00"));
        assertThrows(IllegalArgumentException.class, () -> 
            account.settleFill(BigDecimal.ZERO));
    }

    @Test
    void testSettleFillRejectsNull() {
        account.reserveForOrder(new BigDecimal("2000.00"));
        assertThrows(IllegalArgumentException.class, () -> 
            account.settleFill(null));
    }

    @Test
    void testReleaseReservedFunds() {
        account.reserveForOrder(new BigDecimal("5000.00"));
        account.releaseReservedFunds(new BigDecimal("3000.00"));
        
        assertEquals(new BigDecimal("8000.00"), account.getAvailableBalance());
        assertEquals(new BigDecimal("2000.00"), account.getReservedBalance());
        assertEquals(new BigDecimal("10000.00"), account.getTotalBalance());
    }

    @Test
    void testReleaseReservedFundsAll() {
        account.reserveForOrder(new BigDecimal("5000.00"));
        account.releaseReservedFunds(new BigDecimal("5000.00"));
        
        assertEquals(new BigDecimal("10000.00"), account.getAvailableBalance());
        assertEquals(new BigDecimal("0.00"), account.getReservedBalance());
    }

    @Test
    void testReleaseReservedFundsRejectsExceeding() {
        account.reserveForOrder(new BigDecimal("3000.00"));
        assertThrows(IllegalArgumentException.class, () -> 
            account.releaseReservedFunds(new BigDecimal("3001.00")));
    }

    @Test
    void testReleaseReservedFundsRejectsNegative() {
        account.reserveForOrder(new BigDecimal("2000.00"));
        assertThrows(IllegalArgumentException.class, () -> 
            account.releaseReservedFunds(new BigDecimal("-500.00")));
    }

    @Test
    void testReleaseReservedFundsRejectsZero() {
        account.reserveForOrder(new BigDecimal("2000.00"));
        assertThrows(IllegalArgumentException.class, () -> 
            account.releaseReservedFunds(BigDecimal.ZERO));
    }

    @Test
    void testReleaseReservedFundsRejectsNull() {
        account.reserveForOrder(new BigDecimal("2000.00"));
        assertThrows(IllegalArgumentException.class, () -> 
            account.releaseReservedFunds(null));
    }

    @Test
    void testDepositFunds() {
        account.depositFunds(new BigDecimal("5000.00"));
        assertEquals(new BigDecimal("15000.00"), account.getAvailableBalance());
        assertEquals(new BigDecimal("15000.00"), account.getTotalBalance());
    }

    @Test
    void testDepositFundsSmallAmount() {
        account.depositFunds(new BigDecimal("0.01"));
        assertEquals(new BigDecimal("10000.01"), account.getAvailableBalance());
        assertEquals(new BigDecimal("10000.01"), account.getTotalBalance());
    }

    @Test
    void testDepositFundsMultipleTimes() {
        account.depositFunds(new BigDecimal("1000.00"));
        account.depositFunds(new BigDecimal("2000.00"));
        assertEquals(new BigDecimal("13000.00"), account.getAvailableBalance());
        assertEquals(new BigDecimal("13000.00"), account.getTotalBalance());
    }

    @Test
    void testDepositFundsRejectsNegative() {
        assertThrows(IllegalArgumentException.class, () -> 
            account.depositFunds(new BigDecimal("-1000.00")));
    }

    @Test
    void testDepositFundsRejectsZero() {
        assertThrows(IllegalArgumentException.class, () -> 
            account.depositFunds(BigDecimal.ZERO));
    }

    @Test
    void testDepositFundsRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> 
            account.depositFunds(null));
    }

    @Test
    void testWithdrawFunds() {
        account.withdrawFunds(new BigDecimal("3000.00"));
        assertEquals(new BigDecimal("7000.00"), account.getAvailableBalance());
        assertEquals(new BigDecimal("7000.00"), account.getTotalBalance());
    }

    @Test
    void testWithdrawFundsPartial() {
        account.reserveForOrder(new BigDecimal("2000.00"));
        account.withdrawFunds(new BigDecimal("3000.00"));
        assertEquals(new BigDecimal("5000.00"), account.getAvailableBalance());
        assertEquals(new BigDecimal("7000.00"), account.getTotalBalance());
    }

    @Test
    void testWithdrawFundsFullBalance() {
        account.withdrawFunds(new BigDecimal("10000.00"));
        assertEquals(new BigDecimal("0.00"), account.getAvailableBalance());
        assertEquals(new BigDecimal("0.00"), account.getTotalBalance());
    }

    @Test
    void testWithdrawFundsRejectsExceeding() {
        assertThrows(IllegalArgumentException.class, () -> 
            account.withdrawFunds(new BigDecimal("10001.00")));
    }

    @Test
    void testWithdrawFundsRejectsNegative() {
        assertThrows(IllegalArgumentException.class, () -> 
            account.withdrawFunds(new BigDecimal("-1000.00")));
    }

    @Test
    void testWithdrawFundsRejectsZero() {
        assertThrows(IllegalArgumentException.class, () -> 
            account.withdrawFunds(BigDecimal.ZERO));
    }

    @Test
    void testWithdrawFundsRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> 
            account.withdrawFunds(null));
    }

    @Test
    void testCanCoverOrder() {
        assertTrue(account.canCoverOrder(new BigDecimal("5000.00")));
        assertTrue(account.canCoverOrder(new BigDecimal("10000.00")));
        assertFalse(account.canCoverOrder(new BigDecimal("10001.00")));
    }

    @Test
    void testCanCoverOrderAfterReserve() {
        account.reserveForOrder(new BigDecimal("6000.00"));
        assertTrue(account.canCoverOrder(new BigDecimal("4000.00")));
        assertFalse(account.canCoverOrder(new BigDecimal("4001.00")));
    }

    @Test
    void testCanCoverOrderRejectsNegative() {
        assertThrows(IllegalArgumentException.class, () -> 
            account.canCoverOrder(new BigDecimal("-1000.00")));
    }

    @Test
    void testCanCoverOrderRejectsZero() {
        assertThrows(IllegalArgumentException.class, () -> 
            account.canCoverOrder(BigDecimal.ZERO));
    }

    @Test
    void testCanCoverOrderRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> 
            account.canCoverOrder(null));
    }

    @Test
    void testComplexTradeWorkflow() {
        // Client places BUY order for $3000, must reserve
        assertTrue(account.canCoverOrder(new BigDecimal("3000.00")));
        account.reserveForOrder(new BigDecimal("3000.00"));
        assertEquals(new BigDecimal("7000.00"), account.getAvailableBalance());
        assertEquals(new BigDecimal("3000.00"), account.getReservedBalance());

        // Order is accepted, client places another BUY
        assertTrue(account.canCoverOrder(new BigDecimal("2000.00")));
        account.reserveForOrder(new BigDecimal("2000.00"));
        assertEquals(new BigDecimal("5000.00"), account.getAvailableBalance());
        assertEquals(new BigDecimal("5000.00"), account.getReservedBalance());

        // First order fills for $3000, settle
        account.settleFill(new BigDecimal("3000.00"));
        assertEquals(new BigDecimal("5000.00"), account.getAvailableBalance());
        assertEquals(new BigDecimal("2000.00"), account.getReservedBalance());
        assertEquals(new BigDecimal("7000.00"), account.getTotalBalance());

        // Client deposits more funds
        account.depositFunds(new BigDecimal("5000.00"));
        assertEquals(new BigDecimal("10000.00"), account.getAvailableBalance());
        assertEquals(new BigDecimal("12000.00"), account.getTotalBalance());

        // Second order fills for $2000
        account.settleFill(new BigDecimal("2000.00"));
        assertEquals(new BigDecimal("10000.00"), account.getAvailableBalance());
        assertEquals(new BigDecimal("0.00"), account.getReservedBalance());
        assertEquals(new BigDecimal("10000.00"), account.getTotalBalance());
    }

    @Test
    void testOrderCancellationWorkflow() {
        // Place order and reserve funds
        account.reserveForOrder(new BigDecimal("4000.00"));
        assertEquals(new BigDecimal("6000.00"), account.getAvailableBalance());
        assertEquals(new BigDecimal("4000.00"), account.getReservedBalance());

        // Order is rejected/cancelled - release reserved funds
        account.releaseReservedFunds(new BigDecimal("4000.00"));
        assertEquals(new BigDecimal("10000.00"), account.getAvailableBalance());
        assertEquals(new BigDecimal("0.00"), account.getReservedBalance());
        assertEquals(new BigDecimal("10000.00"), account.getTotalBalance());
    }

    @Test
    void testPartialFillWorkflow() {
        // Reserve for 100 shares at $100 = $10,000
        account.reserveForOrder(new BigDecimal("10000.00"));
        assertEquals(new BigDecimal("0.00"), account.getAvailableBalance());

        // First fill: 60 shares at $100 = $6000
        account.settleFill(new BigDecimal("6000.00"));
        assertEquals(new BigDecimal("0.00"), account.getAvailableBalance());
        assertEquals(new BigDecimal("4000.00"), account.getReservedBalance());
        assertEquals(new BigDecimal("4000.00"), account.getTotalBalance());

        // Second fill: 40 shares at $100 = $4000
        account.settleFill(new BigDecimal("4000.00"));
        assertEquals(new BigDecimal("0.00"), account.getAvailableBalance());
        assertEquals(new BigDecimal("0.00"), account.getReservedBalance());
        assertEquals(new BigDecimal("0.00"), account.getTotalBalance());
    }
}
