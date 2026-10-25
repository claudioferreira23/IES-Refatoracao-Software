package com.saas.billing.adapter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.saas.billing.domain.Money;
import org.junit.jupiter.api.Test;

class TokenValidatingPaymentGatewayTest {

    private static final Money AMOUNT = Money.of("100.00");

    private final TokenValidatingPaymentGateway gateway = new TokenValidatingPaymentGateway();

    @Test
    void approvesTokensStartingWithTokPrefix() {
        assertTrue(gateway.charge("tok_visa", AMOUNT));
        assertTrue(gateway.charge("tok_", AMOUNT));
    }

    @Test
    void declinesTokensWithoutLowercaseTokPrefix() {
        assertFalse(gateway.charge("TOK_x", AMOUNT));
        assertFalse(gateway.charge("visa", AMOUNT));
        assertFalse(gateway.charge("", AMOUNT));
    }

    @Test
    void declinesMissingToken() {
        assertFalse(gateway.charge(null, AMOUNT));
    }
}
