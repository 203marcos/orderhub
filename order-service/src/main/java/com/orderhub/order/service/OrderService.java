package com.orderhub.order.service;

import com.orderhub.order.client.CatalogClient;
import com.orderhub.order.client.PaymentClient;
import com.orderhub.order.dto.CreateOrderRequest;
import com.orderhub.order.dto.OrderResponse;
import com.orderhub.order.entity.Order;
import com.orderhub.order.entity.OrderItem;
import com.orderhub.order.entity.OrderStatus;
import com.orderhub.order.event.OrderCreatedEvent;
import com.orderhub.order.exception.OrderNotFoundException;
import com.orderhub.order.exception.ProductUnavailableException;
import com.orderhub.order.outbox.OutboxEvent;
import com.orderhub.order.outbox.OutboxRepository;
import com.orderhub.order.repository.OrderRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private static final String ORDER_CREATED_TOPIC = "order.created";

    private final OrderRepository orderRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final CatalogClient catalogClient;
    private final PaymentClient paymentClient;

    public OrderService(OrderRepository orderRepository, OutboxRepository outboxRepository,
                        ObjectMapper objectMapper, CatalogClient catalogClient,
                        PaymentClient paymentClient) {
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.catalogClient = catalogClient;
        this.paymentClient = paymentClient;
    }

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request, UUID userId, String userEmail) {
        Order order = new Order(userId, userEmail);

        request.items().forEach(item -> {
            // Resilience (circuit breaker + fallback) lives in the Feign client layer.
            CatalogClient.ProductResponse product = catalogClient.getProduct(item.productId());
            if (!product.available()) {
                throw new ProductUnavailableException(item.productId());
            }
            // Name and price come from the catalog, never from the client.
            order.addItem(new OrderItem(order, product.id(), product.name(), product.price(), item.quantity()));
        });

        order.recalculateTotal();
        Order saved = orderRepository.save(order);

        // The event goes to the outbox, not straight to Kafka: both writes share this
        // transaction, so the order and its OrderCreated event commit or roll back together.
        // A relay publishes it once committed. See OutboxPublisher.
        outboxRepository.save(new OutboxEvent(
                "Order", saved.getId(), "OrderCreated", ORDER_CREATED_TOPIC,
                serialize(toEvent(saved))));

        return OrderResponse.from(saved);
    }

    private String serialize(OrderCreatedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            // An event we cannot serialize is a programming error; failing here rolls the
            // order back rather than committing one that will never reach payment-service.
            throw new IllegalStateException("Could not serialize OrderCreated event", ex);
        }
    }

    /**
     * Reads an order the caller owns. An order belonging to someone else is reported as
     * "not found" rather than "forbidden", so the endpoint cannot be used to probe which
     * order ids exist (OWASP API1 — Broken Object Level Authorization).
     */
    public OrderResponse getOrder(UUID orderId, UUID userId) {
        return orderRepository.findById(orderId)
                .filter(order -> order.getUserId().equals(userId))
                .map(OrderResponse::from)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    public List<OrderResponse> getOrdersByUser(UUID userId) {
        return orderRepository.findByUserId(userId).stream()
                .map(OrderResponse::from)
                .toList();
    }

    /** Fetches the payment detail for an order the caller owns, via payment-service. */
    public PaymentClient.PaymentInfo getOrderPayment(UUID orderId, UUID userId) {
        orderRepository.findById(orderId)
                .filter(order -> order.getUserId().equals(userId))
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        return paymentClient.getPaymentByOrder(orderId, userId);
    }

    @Transactional
    public void confirmOrder(UUID orderId) {
        applySagaOutcome(orderId, OrderStatus.CONFIRMED);
    }

    @Transactional
    public void failOrder(UUID orderId) {
        applySagaOutcome(orderId, OrderStatus.PAYMENT_FAILED);
    }

    /**
     * Applies a payment outcome to an order exactly once.
     *
     * <p>Kafka delivers at least once and gives no ordering guarantee across the
     * {@code payment.approved} and {@code payment.failed} topics, so a redelivered event
     * could otherwise flip an order that has already settled. Only a PENDING order moves.
     */
    private void applySagaOutcome(UUID orderId, OrderStatus outcome) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.getStatus() != OrderStatus.PENDING) {
            log.info("Order {} is already {}, ignoring {} event", orderId, order.getStatus(), outcome);
            return;
        }

        order.setStatus(outcome);
        orderRepository.save(order);
    }

    private OrderCreatedEvent toEvent(Order order) {
        return new OrderCreatedEvent(
                order.getId(),
                order.getUserId(),
                order.getUserEmail(),
                order.getItems().stream()
                        .map(item -> new OrderCreatedEvent.OrderItemEvent(
                                item.getProductId(), item.getProductName(), item.getPrice(), item.getQuantity()))
                        .toList(),
                order.getTotalAmount(),
                order.getCreatedAt()
        );
    }
}
