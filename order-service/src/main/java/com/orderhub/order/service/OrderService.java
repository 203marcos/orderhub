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
import com.orderhub.order.kafka.OrderProducer;
import com.orderhub.order.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderProducer orderProducer;
    private final CatalogClient catalogClient;
    private final PaymentClient paymentClient;

    public OrderService(OrderRepository orderRepository, OrderProducer orderProducer,
                        CatalogClient catalogClient, PaymentClient paymentClient) {
        this.orderRepository = orderRepository;
        this.orderProducer = orderProducer;
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

        orderProducer.publish(toEvent(saved));

        return OrderResponse.from(saved);
    }

    public OrderResponse getOrder(UUID orderId) {
        return orderRepository.findById(orderId)
                .map(OrderResponse::from)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    public List<OrderResponse> getOrdersByUser(UUID userId) {
        return orderRepository.findByUserId(userId).stream()
                .map(OrderResponse::from)
                .toList();
    }

    /** Fetches the payment detail for an order via a synchronous call to payment-service. */
    public PaymentClient.PaymentInfo getOrderPayment(UUID orderId) {
        if (!orderRepository.existsById(orderId)) {
            throw new OrderNotFoundException(orderId);
        }
        return paymentClient.getPaymentByOrder(orderId);
    }

    @Transactional
    public void confirmOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        order.setStatus(OrderStatus.CONFIRMED);
        orderRepository.save(order);
    }

    @Transactional
    public void failOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        order.setStatus(OrderStatus.PAYMENT_FAILED);
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
