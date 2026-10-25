package com.saas.billing.adapter;

import com.saas.billing.domain.Invoice;
import com.saas.billing.port.InfrastructureException;
import com.saas.billing.port.InvoiceRepository;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;
import javax.sql.DataSource;

public class JdbcInvoiceRepository implements InvoiceRepository {

    private static final String INSERT_INVOICE =
            "INSERT INTO invoices(customer_id, amount, status, created_at) VALUES (?, ?, ?, NOW())";

    private final DataSource dataSource;

    public JdbcInvoiceRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public void save(Invoice invoice) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT_INVOICE)) {
            statement.setString(1, invoice.customerId());
            statement.setBigDecimal(2, invoice.amount().amount());
            statement.setString(3, invoice.status().name());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new InfrastructureException("Falha ao registrar a fatura do cliente " + invoice.customerId(), e);
        }
    }
}
