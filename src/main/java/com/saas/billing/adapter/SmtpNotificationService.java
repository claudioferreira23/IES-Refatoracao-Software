package com.saas.billing.adapter;

import com.saas.billing.domain.Money;
import com.saas.billing.port.InfrastructureException;
import com.saas.billing.port.NotificationService;
import java.util.Objects;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.SimpleEmail;

public class SmtpNotificationService implements NotificationService {

    private static final String SUBJECT = "Sua Fatura Mensal";
    private static final String BODY_PREFIX = "Olá! Sua fatura foi processada no valor de R$ ";

    private final String host;
    private final int port;
    private final String sender;

    public SmtpNotificationService(String host, int port, String sender) {
        this.host = Objects.requireNonNull(host, "host");
        this.port = port;
        this.sender = Objects.requireNonNull(sender, "sender");
    }

    @Override
    public void sendInvoiceReceipt(String email, Money amount) {
        try {
            SimpleEmail message = new SimpleEmail();
            message.setHostName(host);
            message.setSmtpPort(port);
            message.setFrom(sender);
            message.setSubject(SUBJECT);
            message.setMsg(BODY_PREFIX + amount);
            message.addTo(email);
            message.send();
        } catch (EmailException e) {
            throw new InfrastructureException("Falha ao enviar o recibo para " + email, e);
        }
    }
}
