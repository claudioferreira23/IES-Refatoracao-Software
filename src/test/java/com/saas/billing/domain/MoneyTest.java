package com.saas.billing.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void normalizesToTwoDecimalPlaces() {
        assertEquals(new BigDecimal("10.00"), Money.of("10").amount());
        assertEquals(Money.of("10.00"), Money.of("10"));
    }

    @Test
    void roundsHalfEven() {
        assertEquals(Money.of("10.00"), Money.of("10.005"));
        assertEquals(Money.of("10.02"), Money.of("10.015"));
    }

    @Test
    void createsFromDoubleWithoutBinaryNoise() {
        assertEquals(Money.of("0.30"), Money.of(0.1 + 0.2));
    }

    @Test
    void arithmeticReturnsNewInstancesAndKeepsOriginalUntouched() {
        Money original = Money.of("100.00");

        Money sum = original.plus(Money.of("0.50"));
        Money difference = original.minus(Money.of("30.00"));
        Money product = original.times(new BigDecimal("0.70"));

        assertEquals(Money.of("100.50"), sum);
        assertEquals(Money.of("70.00"), difference);
        assertEquals(Money.of("70.00"), product);
        assertEquals(Money.of("100.00"), original);
    }

    @Test
    void clampsNegativeValuesToZero() {
        Money negative = Money.of("-5.00");

        assertTrue(negative.isNegative());
        assertEquals(Money.ZERO, negative.atLeastZero());
    }

    @Test
    void keepsPositiveValueWhenClamping() {
        Money positive = Money.of("5.00");

        assertFalse(positive.isNegative());
        assertSame(positive, positive.atLeastZero());
    }

    @Test
    void comparesByAmount() {
        assertTrue(Money.of("1.00").compareTo(Money.of("2.00")) < 0);
        assertEquals(0, Money.of("2").compareTo(Money.of("2.00")));
    }

    @Test
    void formatsAsPlainDecimal() {
        assertEquals("85.00", Money.of("85").toString());
    }

    @Test
    void rejectsNullAmount() {
        assertThrows(NullPointerException.class, () -> new Money(null));
    }
}
