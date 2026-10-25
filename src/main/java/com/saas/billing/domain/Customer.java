package com.saas.billing.domain;

import java.util.Objects;

public record Customer(String id, String email, CustomerStatus status) {

    public Customer {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(status, "status");
    }

    public boolean isActive() {
        return status == CustomerStatus.ACTIVE;
    }
}
