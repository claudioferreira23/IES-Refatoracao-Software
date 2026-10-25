package com.saas.billing.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.saas.billing.domain.Customer;
import com.saas.billing.domain.CustomerStatus;
import com.saas.billing.port.InfrastructureException;
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

    private final List<String> closed = new ArrayList<>();
    private final Map<Integer, Object> parameters = new HashMap<>();

    @Test
    void mapsFoundRowToCustomer() {
        JdbcCustomerRepository repository = new JdbcCustomerRepository(dataSource(row("cliente@exemplo.com", "active")));

        Optional<Customer> customer = repository.findById("cus_1");

        assertEquals(Optional.of(new Customer("cus_1", "cliente@exemplo.com", CustomerStatus.ACTIVE)), customer);
        assertEquals("cus_1", parameters.get(1));
    }

    @Test
    void returnsEmptyWhenCustomerDoesNotExist() {
        JdbcCustomerRepository repository = new JdbcCustomerRepository(dataSource(null));

        assertEquals(Optional.empty(), repository.findById("cus_404"));
    }

    @Test
    void closesResultSetStatementAndConnection() {
        new JdbcCustomerRepository(dataSource(row("cliente@exemplo.com", "ACTIVE"))).findById("cus_1");

        assertEquals(List.of("ResultSet", "PreparedStatement", "Connection"), closed);
    }

    @Test
    void translatesSqlExceptionToInfrastructureException() {
        SQLException failure = new SQLException("Connection refused");
        DataSource unavailable = proxy(DataSource.class, (method, args) -> {
            if (method.equals("getConnection")) {
                throw failure;
            }
            return null;
        });

        InfrastructureException thrown = assertThrows(InfrastructureException.class,
                () -> new JdbcCustomerRepository(unavailable).findById("cus_1"));

        assertSame(failure, thrown.getCause());
        assertTrue(thrown.getMessage().contains("cus_1"));
    }

    private static Map<String, String> row(String email, String status) {
        return Map.of("email", email, "status", status);
    }

    private DataSource dataSource(Map<String, String> row) {
        return proxy(DataSource.class, (method, args) -> method.equals("getConnection") ? connection(row) : null);
    }

    private Connection connection(Map<String, String> row) {
        return proxy(Connection.class, (method, args) -> switch (method) {
            case "prepareStatement" -> statement(row);
            case "close" -> closed.add("Connection");
            default -> null;
        });
    }

    private PreparedStatement statement(Map<String, String> row) {
        return proxy(PreparedStatement.class, (method, args) -> switch (method) {
            case "setString" -> parameters.put((Integer) args[0], args[1]);
            case "executeQuery" -> resultSet(row);
            case "close" -> closed.add("PreparedStatement");
            default -> null;
        });
    }

    private ResultSet resultSet(Map<String, String> row) {
        boolean[] consumed = {false};
        return proxy(ResultSet.class, (method, args) -> switch (method) {
            case "next" -> {
                boolean hasRow = row != null && !consumed[0];
                consumed[0] = true;
                yield hasRow;
            }
            case "getString" -> row.get((String) args[0]);
            case "close" -> closed.add("ResultSet");
            default -> null;
        });
    }

    @FunctionalInterface
    private interface Handler {
        Object handle(String method, Object[] args) throws Throwable;
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Handler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (instance, method, args) -> handler.handle(method.getName(), args));
    }
}
