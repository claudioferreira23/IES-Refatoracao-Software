package com.saas.billing;

import com.saas.billing.domain.BillingCommand;
import com.saas.billing.domain.Customer;
import com.saas.billing.domain.Invoice;
import com.saas.billing.domain.InvoiceStatus;
import com.saas.billing.domain.Money;
import com.saas.billing.port.CustomerRepository;
import com.saas.billing.port.InvoiceRepository;
import com.saas.billing.port.NotificationService;
import com.saas.billing.port.PaymentGateway;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class SubscriptionBillingService {

    private final CustomerRepository customerRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentGateway paymentGateway;
    private final NotificationService notificationService;
    private final Clock clock;

    public SubscriptionBillingService(CustomerRepository customerRepository,
                                      InvoiceRepository invoiceRepository,
                                      PaymentGateway paymentGateway,
                                      NotificationService notificationService,
                                      Clock clock) {
        this.customerRepository = Objects.requireNonNull(customerRepository, "customerRepository");
        this.invoiceRepository = Objects.requireNonNull(invoiceRepository, "invoiceRepository");
        this.paymentGateway = Objects.requireNonNull(paymentGateway, "paymentGateway");
        this.notificationService = Objects.requireNonNull(notificationService, "notificationService");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public List<String> processMonthlyBilling(BillingCommand command) {
        List<String> logs = new ArrayList<>();
        Money calculatedPrice = calculatePrice(command, LocalDate.now(clock));

        Optional<Customer> found = customerRepository.findById(command.customerId());
        if (found.isEmpty()) {
            return logs;
        }
        Customer customer = found.get();

        if (!customer.isActive()) {
            logs.add("Cliente inativo. Cobrança cancelada: " + customer.id());
            return logs;
        }

        boolean paymentSuccess = paymentGateway.charge(command.paymentCardToken(), calculatedPrice);
        if (paymentSuccess) {
            logs.add("Pagamento processado com sucesso: R$ " + calculatedPrice);
        } else {
            logs.add("Falha no pagamento do cartão para: " + customer.id());
        }

        invoiceRepository.save(new Invoice(customer.id(), calculatedPrice, InvoiceStatus.fromPayment(paymentSuccess)));
        notificationService.sendInvoiceReceipt(customer.email(), calculatedPrice);
        logs.add("E-mail disparado para: " + customer.email());

        return logs;
    }

    private Money calculatePrice(BillingCommand command, LocalDate today) {
        LocalDate signupDate = command.signupDate();
        int monthsActive = (today.getYear() - signupDate.getYear()) * 12 + (today.getMonthValue() - signupDate.getMonthValue());

        Money basePrice = command.basePrice();
        Money calculatedPrice = basePrice;

        if (monthsActive > 24) {
            calculatedPrice = calculatedPrice.minus(basePrice.times(new BigDecimal("0.15")));
        } else if (monthsActive > 12) {
            calculatedPrice = calculatedPrice.minus(basePrice.times(new BigDecimal("0.10")));
        }

        if (command.nonProfit()) {
            calculatedPrice = calculatedPrice.times(new BigDecimal("0.70"));
        }

        String promoCode = command.promoCode();
        if (promoCode != null && !promoCode.trim().isEmpty()) {
            if (promoCode.equalsIgnoreCase("BLACKFRIDAY") && today.getMonthValue() == 11) {
                calculatedPrice = calculatedPrice.minus(Money.of("30.00"));
            } else if (promoCode.equalsIgnoreCase("WELCOME10") && monthsActive <= 1) {
                calculatedPrice = calculatedPrice.times(new BigDecimal("0.90"));
            }
        }

        if (command.overdueDays() > 0) {
            Money penalty = calculatedPrice.times(new BigDecimal("0.02"));
            Money dailyInterest = calculatedPrice.times(new BigDecimal("0.001").multiply(BigDecimal.valueOf(command.overdueDays())));
            calculatedPrice = calculatedPrice.plus(penalty).plus(dailyInterest);
        }

        return calculatedPrice.atLeastZero();
    }
}
