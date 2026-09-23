package com.orderhub.notification.service;

import com.orderhub.notification.event.PaymentApprovedEvent;
import com.orderhub.notification.event.PaymentFailedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final JavaMailSender mailSender;

    public NotificationService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendOrderConfirmation(PaymentApprovedEvent event) {
        try {
            mailSender.send(confirmationMessage(event));
            log.info("Sent order confirmation email to {} for order {}", event.userEmail(), event.orderId());
        } catch (Exception ex) {
            log.error("Failed to send confirmation email for order {}", event.orderId(), ex);
        }
    }

    private SimpleMailMessage confirmationMessage(PaymentApprovedEvent event) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(event.userEmail());
        message.setSubject("Order Confirmed - #" + event.orderId());
        message.setText(String.format(
                "Hello!\n\nYour order #%s has been confirmed.\nAmount charged: R$ %.2f\n\nThank you for your purchase!",
                event.orderId(),
                event.amount()
        ));
        return message;
    }

    public void sendPaymentFailedNotification(PaymentFailedEvent event) {
        log.warn("Payment failed for order {} — notification would be sent to userId {}",
                event.orderId(), event.userId());
    }
}
