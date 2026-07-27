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
import com.orderhub.common.outbox.OutboxRecorder;
import com.orderhub.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final OutboxRecorder outboxRecorder;
    private final CatalogClient catalogClient;
    private final PaymentClient paymentClient;

    public OrderService(OrderRepository orderRepository, OutboxRecorder outboxRecorder,
                        CatalogClient catalogClient, PaymentClient paymentClient) {
        this.orderRepository = orderRepository;
        this.outboxRecorder = outboxRecorder;
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

        // Staged in the outbox, not sent to Kafka: both writes share this transaction, so the
        // order and its OrderCreated event commit or roll back together. See OutboxPublisher.
        outboxRecorder.record(toEvent(saved));

        return OrderResponse.from(saved);
    }

    public OrderResponse getOrder(UUID orderId, UUID userId) {
        return OrderResponse.from(ownedOrder(orderId, userId));
    }

    public List<OrderResponse> getOrdersByUser(UUID userId) {
        return orderRepository.findByUserId(userId).stream()
                .map(OrderResponse::from)
                .toList();
    }

    /** Fetches the payment detail for an order the caller owns, via payment-service. */
    public PaymentClient.PaymentInfo getOrderPayment(UUID orderId, UUID userId) {
        ownedOrder(orderId, userId);
        return paymentClient.getPaymentByOrder(orderId, userId);
    }

    /**
     * Loads an order only if the caller owns it.
     *
     * <p>An order belonging to someone else is reported as "not found" rather than "forbidden",
     * so the endpoint cannot be used to probe which order ids exist
     * (OWASP API1 — Broken Object Level Authorization).
     */
    private Order ownedOrder(UUID orderId, UUID userId) {
        return orderRepository.findById(orderId)
                .filter(order -> order.getUserId().equals(userId))
                .orElseThrow(() -> new OrderNotFoundException(orderId));
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
