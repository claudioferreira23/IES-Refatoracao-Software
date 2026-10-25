package com.saas.billing.domain;

import java.util.Objects;

public record Invoice(String customerId, Money amount, InvoiceStatus status) {

    public Invoice {
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(status, "status");
    }
}
