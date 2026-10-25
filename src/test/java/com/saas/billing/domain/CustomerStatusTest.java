package com.saas.billing.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CustomerStatusTest {

    @Test
    void treatsActiveCodeAsActiveIgnoringCase() {
        assertEquals(CustomerStatus.ACTIVE, CustomerStatus.fromCode("ACTIVE"));
        assertEquals(CustomerStatus.ACTIVE, CustomerStatus.fromCode("active"));
        assertEquals(CustomerStatus.ACTIVE, CustomerStatus.fromCode("Active"));
    }

    @Test
    void treatsAnyOtherCodeAsInactive() {
        assertEquals(CustomerStatus.INACTIVE, CustomerStatus.fromCode("INACTIVE"));
        assertEquals(CustomerStatus.INACTIVE, CustomerStatus.fromCode("SUSPENDED"));
        assertEquals(CustomerStatus.INACTIVE, CustomerStatus.fromCode(" ACTIVE "));
    }

    @Test
    void treatsMissingCodeAsInactive() {
        assertEquals(CustomerStatus.INACTIVE, CustomerStatus.fromCode(null));
    }
}
