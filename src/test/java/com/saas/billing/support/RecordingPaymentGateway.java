package com.saas.billing.support;

import com.saas.billing.domain.Money;
import com.saas.billing.port.PaymentGateway;
import java.util.ArrayList;
import java.util.List;

public class RecordingPaymentGateway implements PaymentGateway {

    private final boolean approve;
    private final List<Money> charges = new ArrayList<>();

    private RecordingPaymentGateway(boolean approve) {
        this.approve = approve;
    }

    public static RecordingPaymentGateway approving() {
        return new RecordingPaymentGateway(true);
    }

    public static RecordingPaymentGateway declining() {
        return new RecordingPaymentGateway(false);
    }

    @Override
    public boolean charge(String cardToken, Money amount) {
        charges.add(amount);
        return approve;
    }

    public List<Money> charges() {
        return List.copyOf(charges);
    }
}
