package com.saas.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.saas.billing.domain.BillingCommand;
import com.saas.billing.domain.Customer;
import com.saas.billing.domain.CustomerStatus;
import com.saas.billing.domain.Invoice;
import com.saas.billing.domain.InvoiceStatus;
import com.saas.billing.domain.Money;
import com.saas.billing.domain.PricingCalculator;
import com.saas.billing.port.InfrastructureException;
import com.saas.billing.support.InMemoryCustomerRepository;
import com.saas.billing.support.InMemoryInvoiceRepository;
import com.saas.billing.support.RecordingNotificationService;
import com.saas.billing.support.RecordingNotificationService.SentReceipt;
import com.saas.billing.support.RecordingPaymentGateway;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class SubscriptionBillingServiceTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final Clock MARCH_CLOCK = Clock.fixed(Instant.parse("2026-03-15T12:00:00Z"), SAO_PAULO);
    private static final Clock NOVEMBER_CLOCK = Clock.fixed(Instant.parse("2026-11-27T12:00:00Z"), SAO_PAULO);

    private static final Customer ACTIVE_CUSTOMER =
            new Customer("cus_1", "cliente@exemplo.com", CustomerStatus.ACTIVE);
    private static final Customer INACTIVE_CUSTOMER =
            new Customer("cus_2", "inativo@exemplo.com", CustomerStatus.INACTIVE);

    private final InMemoryCustomerRepository customers =
            new InMemoryCustomerRepository().with(ACTIVE_CUSTOMER).with(INACTIVE_CUSTOMER);
    private final InMemoryInvoiceRepository invoices = new InMemoryInvoiceRepository();
    private final RecordingNotificationService notifications = new RecordingNotificationService();

    @Test
    void chargesActiveCustomerRecordsPaidInvoiceAndSendsReceipt() {
        RecordingPaymentGateway gateway = RecordingPaymentGateway.approving();

        List<String> logs = service(gateway, MARCH_CLOCK).processMonthlyBilling(command("cus_1", null));

        assertEquals(List.of(
                "Pagamento processado com sucesso: R$ 100.00",
                "E-mail disparado para: cliente@exemplo.com"), logs);
        assertEquals(List.of(Money.of("100.00")), gateway.charges());
        assertEquals(List.of(new Invoice("cus_1", Money.of("100.00"), InvoiceStatus.PAID)), invoices.invoices());
        assertEquals(List.of(new SentReceipt("cliente@exemplo.com", Money.of("100.00"))), notifications.sent());
    }

    @Test
    void recordsFailedInvoiceAndStillSendsReceiptWhenPaymentIsDeclined() {
        List<String> logs = service(RecordingPaymentGateway.declining(), MARCH_CLOCK)
                .processMonthlyBilling(command("cus_1", null));

        assertEquals(List.of(
                "Falha no pagamento do cartão para: cus_1",
                "E-mail disparado para: cliente@exemplo.com"), logs);
        assertEquals(List.of(new Invoice("cus_1", Money.of("100.00"), InvoiceStatus.FAILED)), invoices.invoices());
        assertEquals(1, notifications.sent().size());
    }

    @Test
    void cancelsBillingForInactiveCustomer() {
        RecordingPaymentGateway gateway = RecordingPaymentGateway.approving();

        List<String> logs = service(gateway, MARCH_CLOCK).processMonthlyBilling(command("cus_2", null));

        assertEquals(List.of("Cliente inativo. Cobrança cancelada: cus_2"), logs);
        assertTrue(gateway.charges().isEmpty());
        assertTrue(invoices.invoices().isEmpty());
        assertTrue(notifications.sent().isEmpty());
    }

    @Test
    void doesNothingForUnknownCustomer() {
        RecordingPaymentGateway gateway = RecordingPaymentGateway.approving();

        List<String> logs = service(gateway, MARCH_CLOCK).processMonthlyBilling(command("cus_404", null));

        assertTrue(logs.isEmpty());
        assertTrue(gateway.charges().isEmpty());
        assertTrue(invoices.invoices().isEmpty());
        assertTrue(notifications.sent().isEmpty());
    }

    @Test
    void usesInjectedClockToDecideDateSensitiveRules() {
        RecordingPaymentGateway gateway = RecordingPaymentGateway.approving();

        service(gateway, NOVEMBER_CLOCK).processMonthlyBilling(command("cus_1", "BLACKFRIDAY"));
        service(gateway, MARCH_CLOCK).processMonthlyBilling(command("cus_1", "BLACKFRIDAY"));

        assertEquals(List.of(Money.of("70.00"), Money.of("100.00")), gateway.charges());
    }

    @Test
    void propagatesInfrastructureFailuresInsteadOfSilencingThem() {
        InfrastructureException failure = new InfrastructureException("banco fora do ar", new RuntimeException());
        SubscriptionBillingService service = new SubscriptionBillingService(
                customerId -> {
                    throw failure;
                },
                invoices, RecordingPaymentGateway.approving(), notifications, new PricingCalculator(), MARCH_CLOCK);

        InfrastructureException thrown =
                assertThrows(InfrastructureException.class, () -> service.processMonthlyBilling(command("cus_1", null)));

        assertSame(failure, thrown);
        assertTrue(invoices.invoices().isEmpty());
    }

    @Test
    void keepsPaidInvoiceAndLogsWhenReceiptCannotBeSent() {
        notifications.failOnSend(new InfrastructureException("smtp fora do ar", new RuntimeException()));
        RecordingPaymentGateway gateway = RecordingPaymentGateway.approving();

        List<String> logs = service(gateway, MARCH_CLOCK).processMonthlyBilling(command("cus_1", null));

        assertEquals(List.of(
                "Pagamento processado com sucesso: R$ 100.00",
                "Falha ao enviar recibo para: cliente@exemplo.com"), logs);
        assertEquals(List.of(Money.of("100.00")), gateway.charges());
        assertEquals(List.of(new Invoice("cus_1", Money.of("100.00"), InvoiceStatus.PAID)), invoices.invoices());
        assertTrue(notifications.sent().isEmpty());
    }

    @Test
    void propagatesInvoiceStorageFailureAfterCharging() {
        InfrastructureException failure = new InfrastructureException("falha ao gravar fatura", new RuntimeException());
        invoices.failOnSave(failure);
        RecordingPaymentGateway gateway = RecordingPaymentGateway.approving();
        SubscriptionBillingService service = service(gateway, MARCH_CLOCK);

        InfrastructureException thrown =
                assertThrows(InfrastructureException.class, () -> service.processMonthlyBilling(command("cus_1", null)));

        assertSame(failure, thrown);
        assertEquals(List.of(Money.of("100.00")), gateway.charges());
        assertTrue(notifications.sent().isEmpty());
    }

    private SubscriptionBillingService service(RecordingPaymentGateway gateway, Clock clock) {
        return new SubscriptionBillingService(customers, invoices, gateway, notifications, new PricingCalculator(), clock);
    }

    private static BillingCommand command(String customerId, String promoCode) {
        return new BillingCommand(
                customerId, Money.of("100.00"), LocalDate.of(2026, 1, 10), 0, promoCode, false, "tok_visa");
    }
}
