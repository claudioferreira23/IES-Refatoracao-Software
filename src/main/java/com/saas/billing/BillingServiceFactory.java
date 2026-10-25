package com.saas.billing;

import com.saas.billing.adapter.JdbcCustomerRepository;
import com.saas.billing.adapter.JdbcInvoiceRepository;
import com.saas.billing.adapter.SmtpNotificationService;
import com.saas.billing.adapter.TokenValidatingPaymentGateway;
import com.saas.billing.domain.PricingCalculator;
import java.time.Clock;
import java.util.Optional;
import javax.sql.DataSource;
import org.postgresql.ds.PGSimpleDataSource;

public final class BillingServiceFactory {

    private BillingServiceFactory() {
    }

    public static SubscriptionBillingService createDefault() {
        DataSource dataSource = postgresDataSource();
        return new SubscriptionBillingService(
                new JdbcCustomerRepository(dataSource),
                new JdbcInvoiceRepository(dataSource),
                new TokenValidatingPaymentGateway(),
                new SmtpNotificationService(
                        env("BILLING_SMTP_HOST", "smtp.empresa.com"),
                        Integer.parseInt(env("BILLING_SMTP_PORT", "587")),
                        env("BILLING_SMTP_FROM", "billing@empresa.com")),
                new PricingCalculator(),
                Clock.systemDefaultZone());
    }

    private static DataSource postgresDataSource() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(requiredEnv("BILLING_DB_URL"));
        dataSource.setUser(requiredEnv("BILLING_DB_USER"));
        dataSource.setPassword(env("BILLING_DB_PASSWORD", ""));
        return dataSource;
    }

    private static String env(String name, String defaultValue) {
        return Optional.ofNullable(System.getenv(name)).orElse(defaultValue);
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Variável de ambiente obrigatória não definida: " + name);
        }
        return value;
    }
}
