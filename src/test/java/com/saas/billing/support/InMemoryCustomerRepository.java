package com.saas.billing.support;

import com.saas.billing.domain.Customer;
import com.saas.billing.port.CustomerRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class InMemoryCustomerRepository implements CustomerRepository {

    private final Map<String, Customer> customers = new HashMap<>();

    public InMemoryCustomerRepository with(Customer customer) {
        customers.put(customer.id(), customer);
        return this;
    }

    @Override
    public Optional<Customer> findById(String customerId) {
        return Optional.ofNullable(customers.get(customerId));
    }
}
