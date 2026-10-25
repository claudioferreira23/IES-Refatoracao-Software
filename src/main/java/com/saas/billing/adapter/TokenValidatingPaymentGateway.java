package com.saas.billing.adapter;

import com.saas.billing.domain.Money;
import com.saas.billing.port.PaymentGateway;

public class TokenValidatingPaymentGateway implements PaymentGateway {

    private static final String VALID_TOKEN_PREFIX = "tok_";

    @Override
    public boolean charge(String cardToken, Money amount) {
        return cardToken != null && cardToken.startsWith(VALID_TOKEN_PREFIX);
    }
}
