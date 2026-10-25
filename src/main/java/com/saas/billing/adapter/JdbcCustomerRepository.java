package com.saas.billing.adapter;

import com.saas.billing.domain.Customer;
import com.saas.billing.domain.CustomerStatus;
import com.saas.billing.port.CustomerRepository;
import com.saas.billing.port.InfrastructureException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import javax.sql.DataSource;

public class JdbcCustomerRepository implements CustomerRepository {

    private static final String FIND_BY_ID = "SELECT email, status FROM customers WHERE id = ?";

    private final DataSource dataSource;

    public JdbcCustomerRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public Optional<Customer> findById(String customerId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_BY_ID)) {
            statement.setString(1, customerId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(toCustomer(customerId, result)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new InfrastructureException("Falha ao consultar o cliente " + customerId, e);
        }
    }

    private Customer toCustomer(String customerId, ResultSet result) throws SQLException {
        return new Customer(customerId, result.getString("email"), CustomerStatus.fromCode(result.getString("status")));
    }
}
