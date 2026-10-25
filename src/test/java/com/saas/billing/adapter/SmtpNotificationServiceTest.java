package com.saas.billing.adapter;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.saas.billing.domain.Money;
import com.saas.billing.port.InfrastructureException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SmtpNotificationServiceTest {

    private static final Money AMOUNT = Money.of("100.00");

    private final SmtpNotificationService service =
            new SmtpNotificationService("127.0.0.1", 1, "billing@empresa.com");

    @ParameterizedTest(name = "e-mail ''{0}''")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsMissingEmailBeforeAnySendAttempt(String email) {
        InfrastructureException thrown =
                assertThrows(InfrastructureException.class, () -> service.sendInvoiceReceipt(email, AMOUNT));

        assertNull(thrown.getCause());
    }
}
