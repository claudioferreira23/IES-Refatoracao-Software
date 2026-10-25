package com.saas.billing.support;

import com.saas.billing.domain.Invoice;
import com.saas.billing.port.InvoiceRepository;
import java.util.ArrayList;
import java.util.List;

public class InMemoryInvoiceRepository implements InvoiceRepository {

    private final List<Invoice> invoices = new ArrayList<>();

    @Override
    public void save(Invoice invoice) {
        invoices.add(invoice);
    }

    public List<Invoice> invoices() {
        return List.copyOf(invoices);
    }
}
