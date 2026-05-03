package com.orderhub.notification.service;

import com.orderhub.notification.event.PaymentApprovedEvent;
import com.orderhub.notification.event.PaymentFailedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock JavaMailSender mailSender;

    @InjectMocks NotificationService notificationService;

    @Test
    void shouldSendConfirmationEmailOnPaymentApproved() {
        PaymentApprovedEvent event = new PaymentApprovedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "customer@example.com",
                new BigDecimal("49.99"),
                LocalDateTime.now()
        );

        notificationService.sendOrderConfirmation(event);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage sent = captor.getValue();

        assertThat(sent.getTo()).containsExactly("customer@example.com");
        assertThat(sent.getSubject()).contains(event.orderId().toString());
        assertThat(sent.getText()).contains("49");
    }

    @Test
    void shouldHandleMailSenderExceptionGracefully() {
        PaymentApprovedEvent event = new PaymentApprovedEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "bad@example.com", new BigDecimal("10.00"), LocalDateTime.now()
        );
        doThrow(new RuntimeException("SMTP error")).when(mailSender).send(any(SimpleMailMessage.class));

        notificationService.sendOrderConfirmation(event);

        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void shouldLogPaymentFailedNotification() {
        PaymentFailedEvent event = new PaymentFailedEvent(
                UUID.randomUUID(), UUID.randomUUID(), "Insufficient funds", LocalDateTime.now()
        );

        notificationService.sendPaymentFailedNotification(event);

        verifyNoInteractions(mailSender);
    }
}
