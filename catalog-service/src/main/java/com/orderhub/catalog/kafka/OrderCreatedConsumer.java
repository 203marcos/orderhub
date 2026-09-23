package com.orderhub.catalog.kafka;

import com.orderhub.catalog.event.OrderCreatedEvent;
import com.orderhub.catalog.service.StockReservationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderCreatedConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedConsumer.class);

    private final StockReservationService stockReservationService;

    public OrderCreatedConsumer(StockReservationService stockReservationService) {
        this.stockReservationService = stockReservationService;
    }

    @KafkaListener(topics = "order.created", groupId = "catalog-service")
    public void onOrderCreated(OrderCreatedEvent event) {
        log.info("Received OrderCreated event for order {}", event.orderId());
        stockReservationService.reserveStock(event);
    }
}
