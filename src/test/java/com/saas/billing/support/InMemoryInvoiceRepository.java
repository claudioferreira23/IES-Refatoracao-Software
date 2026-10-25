package com.saas.billing.support;

import com.saas.billing.domain.Invoice;
import com.saas.billing.port.InvoiceRepository;
import java.util.ArrayList;
import java.util.List;

public class InMemoryInvoiceRepository implements InvoiceRepository {

    private final List<Invoice> invoices = new ArrayList<>();
    private RuntimeException failure;

    public InMemoryInvoiceRepository failOnSave(RuntimeException failure) {
        this.failure = failure;
        return this;
    }

    @Override
    public void save(Invoice invoice) {
        if (failure != null) {
            throw failure;
        }
        invoices.add(invoice);
    }

    public List<Invoice> invoices() {
        return List.copyOf(invoices);
    }
}
