package com.saas.billing.support;

import com.saas.billing.domain.Money;
import com.saas.billing.port.NotificationService;
import java.util.ArrayList;
import java.util.List;

public class RecordingNotificationService implements NotificationService {

    public record SentReceipt(String email, Money amount) {
    }

    private final List<SentReceipt> sent = new ArrayList<>();
    private RuntimeException failure;

    public RecordingNotificationService failOnSend(RuntimeException failure) {
        this.failure = failure;
        return this;
    }

    @Override
    public void sendInvoiceReceipt(String email, Money amount) {
        if (failure != null) {
            throw failure;
        }
        sent.add(new SentReceipt(email, amount));
    }

    public List<SentReceipt> sent() {
        return List.copyOf(sent);
    }
}
