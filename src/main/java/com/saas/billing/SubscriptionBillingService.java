package com.saas.billing;

import com.saas.billing.domain.BillingCommand;
import com.saas.billing.domain.Customer;
import com.saas.billing.domain.Invoice;
import com.saas.billing.domain.InvoiceStatus;
import com.saas.billing.domain.Money;
import com.saas.billing.domain.PricingCalculator;
import com.saas.billing.port.CustomerRepository;
import com.saas.billing.port.InfrastructureException;
import com.saas.billing.port.InvoiceRepository;
import com.saas.billing.port.NotificationService;
import com.saas.billing.port.PaymentGateway;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SubscriptionBillingService {

    private final CustomerRepository customerRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentGateway paymentGateway;
    private final NotificationService notificationService;
    private final PricingCalculator pricingCalculator;
    private final Clock clock;

    public SubscriptionBillingService(CustomerRepository customerRepository,
                                      InvoiceRepository invoiceRepository,
                                      PaymentGateway paymentGateway,
                                      NotificationService notificationService,
                                      PricingCalculator pricingCalculator,
                                      Clock clock) {
        this.customerRepository = Objects.requireNonNull(customerRepository, "customerRepository");
        this.invoiceRepository = Objects.requireNonNull(invoiceRepository, "invoiceRepository");
        this.paymentGateway = Objects.requireNonNull(paymentGateway, "paymentGateway");
        this.notificationService = Objects.requireNonNull(notificationService, "notificationService");
        this.pricingCalculator = Objects.requireNonNull(pricingCalculator, "pricingCalculator");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public List<String> processMonthlyBilling(BillingCommand command) {
        Money amount = pricingCalculator.calculate(command, LocalDate.now(clock));
        List<String> logs = new ArrayList<>();
        customerRepository.findById(command.customerId())
                .ifPresent(customer -> bill(customer, command, amount, logs));
        return logs;
    }

    private void bill(Customer customer, BillingCommand command, Money amount, List<String> logs) {
        if (!customer.isActive()) {
            logs.add("Cliente inativo. Cobrança cancelada: " + customer.id());
            return;
        }
        boolean approved = paymentGateway.charge(command.paymentCardToken(), amount);
        logs.add(approved
                ? "Pagamento processado com sucesso: R$ " + amount
                : "Falha no pagamento do cartão para: " + customer.id());
        invoiceRepository.save(new Invoice(customer.id(), amount, InvoiceStatus.fromPayment(approved)));
        logs.add(sendReceipt(customer.email(), amount));
    }

    private String sendReceipt(String email, Money amount) {
        try {
            notificationService.sendInvoiceReceipt(email, amount);
            return "E-mail disparado para: " + email;
        } catch (InfrastructureException e) {
            return "Falha ao enviar recibo para: " + email;
        }
    }
}
