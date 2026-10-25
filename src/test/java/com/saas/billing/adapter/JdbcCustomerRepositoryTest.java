package com.saas.billing.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.saas.billing.domain.Customer;
import com.saas.billing.domain.CustomerStatus;
import com.saas.billing.port.InfrastructureException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class JdbcCustomerRepositoryTest {

    private static final String CUSTOMER_ID = "cus_1";
    private static final String CUSTOMER_EMAIL = "cliente@exemplo.com";
    private static final String CLOSE = "close";

    private final List<String> closed = new ArrayList<>();
    private final Map<Integer, Object> parameters = new HashMap<>();

    @Test
    void mapsFoundRowToCustomer() {
        JdbcCustomerRepository repository = new JdbcCustomerRepository(dataSource(row(CUSTOMER_EMAIL, "active")));

        Optional<Customer> customer = repository.findById(CUSTOMER_ID);

        assertEquals(Optional.of(new Customer(CUSTOMER_ID, CUSTOMER_EMAIL, CustomerStatus.ACTIVE)), customer);
        assertEquals(CUSTOMER_ID, parameters.get(1));
    }

    @Test
    void returnsEmptyWhenCustomerDoesNotExist() {
        JdbcCustomerRepository repository = new JdbcCustomerRepository(dataSource(null));

        assertEquals(Optional.empty(), repository.findById("cus_404"));
    }

    @Test
    void closesResultSetStatementAndConnection() {
        new JdbcCustomerRepository(dataSource(row(CUSTOMER_EMAIL, "ACTIVE"))).findById(CUSTOMER_ID);

        assertEquals(List.of("ResultSet", "PreparedStatement", "Connection"), closed);
    }

    @Test
    void translatesSqlExceptionToInfrastructureException() {
        SQLException failure = new SQLException("Connection refused");
        DataSource unavailable = proxy(DataSource.class, (instance, method, args) -> {
            if (method.getName().equals("getConnection")) {
                throw failure;
            }
            return null;
        });
        JdbcCustomerRepository repository = new JdbcCustomerRepository(unavailable);

        InfrastructureException thrown = assertThrows(InfrastructureException.class,
                () -> repository.findById(CUSTOMER_ID));

        assertSame(failure, thrown.getCause());
        assertTrue(thrown.getMessage().contains(CUSTOMER_ID));
    }

    private static Map<String, String> row(String email, String status) {
        return Map.of("email", email, "status", status);
    }

    private DataSource dataSource(Map<String, String> row) {
        return proxy(DataSource.class,
                (instance, method, args) -> method.getName().equals("getConnection") ? connection(row) : null);
    }

    private Connection connection(Map<String, String> row) {
        return proxy(Connection.class, (instance, method, args) -> switch (method.getName()) {
            case "prepareStatement" -> statement(row);
            case CLOSE -> closed.add("Connection");
            default -> null;
        });
    }

    private PreparedStatement statement(Map<String, String> row) {
        return proxy(PreparedStatement.class, (instance, method, args) -> switch (method.getName()) {
            case "setString" -> parameters.put((Integer) args[0], args[1]);
            case "executeQuery" -> resultSet(row);
            case CLOSE -> closed.add("PreparedStatement");
            default -> null;
        });
    }

    private ResultSet resultSet(Map<String, String> row) {
        boolean[] consumed = {false};
        return proxy(ResultSet.class, (instance, method, args) -> switch (method.getName()) {
            case "next" -> {
                boolean hasRow = row != null && !consumed[0];
                consumed[0] = true;
                yield hasRow;
            }
            case "getString" -> row.get((String) args[0]);
            case CLOSE -> closed.add("ResultSet");
            default -> null;
        });
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }
}
