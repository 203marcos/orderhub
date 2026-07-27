package com.orderhub.catalog.kafka;

import com.orderhub.catalog.event.OrderCancelledEvent;
import com.orderhub.catalog.event.PaymentFailedEvent;
import com.orderhub.catalog.service.StockReservationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Two independent triggers for the same compensating action: a payment can fail after the
 * order was accepted, or the order itself can be cancelled. Both end up releasing the same
 * stock reservation, and {@link StockReservationService#releaseReservation} is what makes only
 * one of them actually move stock, regardless of arrival order or redelivery.
 */
@Component
public class StockReleaseConsumer {

    private static final Logger log = LoggerFactory.getLogger(StockReleaseConsumer.class);

    private final StockReservationService stockReservationService;

    public StockReleaseConsumer(StockReservationService stockReservationService) {
        this.stockReservationService = stockReservationService;
    }

    @KafkaListener(topics = "payment.failed", groupId = "catalog-service")
    public void onPaymentFailed(PaymentFailedEvent event) {
        log.info("Received PaymentFailed event for order {}", event.orderId());
        stockReservationService.releaseReservation(event.orderId(), "payment.failed");
    }

    @KafkaListener(topics = "order.cancelled", groupId = "catalog-service")
    public void onOrderCancelled(OrderCancelledEvent event) {
        log.info("Received OrderCancelled event for order {}", event.orderId());
        stockReservationService.releaseReservation(event.orderId(), "order.cancelled");
    }
}
