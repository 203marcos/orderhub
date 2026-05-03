package com.orderhub.order.service;

import com.orderhub.order.client.CatalogClient;
import com.orderhub.order.dto.CreateOrderRequest;
import com.orderhub.order.dto.OrderResponse;
import com.orderhub.order.entity.Order;
import com.orderhub.order.entity.OrderItem;
import com.orderhub.order.entity.OrderStatus;
import com.orderhub.order.event.OrderCreatedEvent;
import com.orderhub.order.exception.OrderNotFoundException;
import com.orderhub.order.kafka.OrderProducer;
import com.orderhub.order.repository.OrderRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderProducer orderProducer;
    private final CatalogClient catalogClient;

    public OrderService(OrderRepository orderRepository, OrderProducer orderProducer, CatalogClient catalogClient) {
        this.orderRepository = orderRepository;
        this.orderProducer = orderProducer;
        this.catalogClient = catalogClient;
    }

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request, UUID userId, String userEmail) {
        BigDecimal total = request.items().stream()
                .map(i -> i.price().multiply(BigDecimal.valueOf(i.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = new Order(userId, userEmail, total);

        request.items().forEach(i -> {
            BigDecimal price = resolvePrice(i.productId(), i.price());
            order.addItem(new OrderItem(order, i.productId(), i.productName(), price, i.quantity()));
        });

        Order saved = orderRepository.save(order);

        OrderCreatedEvent event = new OrderCreatedEvent(
                saved.getId(),
                saved.getUserId(),
                saved.getUserEmail(),
                saved.getItems().stream()
                        .map(item -> new OrderCreatedEvent.OrderItemEvent(
                                item.getProductId(), item.getProductName(), item.getPrice(), item.getQuantity()))
                        .toList(),
                saved.getTotalAmount(),
                saved.getCreatedAt()
        );
        orderProducer.publish(event);

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

    @CircuitBreaker(name = "catalogService", fallbackMethod = "resolvePriceFallback")
    BigDecimal resolvePrice(UUID productId, BigDecimal providedPrice) {
        CatalogClient.ProductResponse product = catalogClient.getProduct(productId);
        return product.price();
    }

    BigDecimal resolvePriceFallback(UUID productId, BigDecimal providedPrice, Exception ex) {
        return providedPrice;
    }
}
