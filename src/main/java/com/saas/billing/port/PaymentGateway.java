package com.saas.billing.port;

import com.saas.billing.domain.Money;

public interface PaymentGateway {

    boolean charge(String cardToken, Money amount);
}
