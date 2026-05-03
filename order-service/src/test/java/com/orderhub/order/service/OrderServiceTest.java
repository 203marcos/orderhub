package com.orderhub.order.service;

import com.orderhub.order.client.CatalogClient;
import com.orderhub.order.dto.CreateOrderRequest;
import com.orderhub.order.dto.OrderItemRequest;
import com.orderhub.order.dto.OrderResponse;
import com.orderhub.order.entity.Order;
import com.orderhub.order.entity.OrderItem;
import com.orderhub.order.entity.OrderStatus;
import com.orderhub.order.exception.OrderNotFoundException;
import com.orderhub.order.kafka.OrderProducer;
import com.orderhub.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock OrderRepository orderRepository;
    @Mock OrderProducer orderProducer;
    @Mock CatalogClient catalogClient;

    @InjectMocks OrderService orderService;

    private UUID userId;
    private String userEmail;
    private UUID productId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        userEmail = "user@example.com";
        productId = UUID.randomUUID();
    }

    @Test
    void shouldCreateOrder() {
        CreateOrderRequest request = new CreateOrderRequest(List.of(
                new OrderItemRequest(productId, "Product A", new BigDecimal("29.99"), 2)
        ));

        Order savedOrder = new Order(userId, userEmail, new BigDecimal("59.98"));
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
        when(catalogClient.getProduct(productId))
                .thenReturn(new CatalogClient.ProductResponse(productId, "Product A", new BigDecimal("29.99"), true));

        OrderResponse response = orderService.createOrder(request, userId, userEmail);

        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo(userId);
        verify(orderRepository).save(any(Order.class));
        verify(orderProducer).publish(any());
    }

    @Test
    void shouldFallbackToprovidedPriceWhenCatalogUnavailable() {
        CreateOrderRequest request = new CreateOrderRequest(List.of(
                new OrderItemRequest(productId, "Product A", new BigDecimal("29.99"), 1)
        ));

        when(catalogClient.getProduct(any())).thenThrow(new RuntimeException("catalog unavailable"));
        Order savedOrder = new Order(userId, userEmail, new BigDecimal("29.99"));
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        OrderResponse response = orderService.createOrder(request, userId, userEmail);

        assertThat(response).isNotNull();
        verify(orderProducer).publish(any());
    }

    @Test
    void shouldGetOrderById() {
        UUID orderId = UUID.randomUUID();
        Order order = new Order(userId, userEmail, new BigDecimal("50.00"));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.getOrder(orderId);

        assertThat(response.userId()).isEqualTo(userId);
    }

    @Test
    void shouldThrowWhenOrderNotFound() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrder(orderId))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void shouldGetOrdersByUser() {
        Order order1 = new Order(userId, userEmail, new BigDecimal("10.00"));
        Order order2 = new Order(userId, userEmail, new BigDecimal("20.00"));
        when(orderRepository.findByUserId(userId)).thenReturn(List.of(order1, order2));

        List<OrderResponse> orders = orderService.getOrdersByUser(userId);

        assertThat(orders).hasSize(2);
    }

    @Test
    void shouldConfirmOrder() {
        UUID orderId = UUID.randomUUID();
        Order order = mock(Order.class);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        orderService.confirmOrder(orderId);

        verify(order).setStatus(OrderStatus.CONFIRMED);
        verify(orderRepository).save(order);
    }

    @Test
    void shouldFailOrder() {
        UUID orderId = UUID.randomUUID();
        Order order = mock(Order.class);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        orderService.failOrder(orderId);

        verify(order).setStatus(OrderStatus.PAYMENT_FAILED);
    }

    @Test
    void shouldThrowWhenConfirmingNonExistentOrder() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.confirmOrder(orderId))
                .isInstanceOf(OrderNotFoundException.class);
    }
}
