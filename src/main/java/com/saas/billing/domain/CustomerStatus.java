package com.saas.billing.domain;

public enum CustomerStatus {
    ACTIVE,
    INACTIVE;

    public static CustomerStatus fromCode(String code) {
        return ACTIVE.name().equalsIgnoreCase(code) ? ACTIVE : INACTIVE;
    }
}
