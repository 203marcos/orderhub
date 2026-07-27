package com.orderhub.order.service;

import com.orderhub.order.client.CatalogClient;
import com.orderhub.order.client.PaymentClient;
import com.orderhub.order.dto.CreateOrderRequest;
import com.orderhub.order.dto.OrderItemRequest;
import com.orderhub.order.dto.OrderResponse;
import com.orderhub.order.entity.Order;
import com.orderhub.order.entity.OrderItem;
import com.orderhub.order.entity.OrderStatus;
import com.orderhub.order.exception.OrderNotFoundException;
import com.orderhub.order.exception.ProductUnavailableException;
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
    @Mock PaymentClient paymentClient;

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
    void shouldCreateOrderUsingCatalogPrice() {
        CreateOrderRequest request = new CreateOrderRequest(List.of(
                new OrderItemRequest(productId, 2)
        ));

        when(catalogClient.getProduct(productId))
                .thenReturn(new CatalogClient.ProductResponse(productId, "Product A", new BigDecimal("29.99"), true));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.createOrder(request, userId, userEmail);

        assertThat(response.userId()).isEqualTo(userId);
        // Total is computed server-side from the catalog price (29.99 * 2), never from the client.
        assertThat(response.totalAmount()).isEqualByComparingTo(new BigDecimal("59.98"));
        assertThat(response.items()).singleElement()
                .satisfies(item -> assertThat(item.price()).isEqualByComparingTo(new BigDecimal("29.99")));
        verify(orderRepository).save(any(Order.class));
        verify(orderProducer).publish(any());
    }

    @Test
    void shouldRejectUnavailableProduct() {
        CreateOrderRequest request = new CreateOrderRequest(List.of(
                new OrderItemRequest(productId, 1)
        ));
        when(catalogClient.getProduct(productId))
                .thenReturn(new CatalogClient.ProductResponse(productId, "Product A", new BigDecimal("29.99"), false));

        assertThatThrownBy(() -> orderService.createOrder(request, userId, userEmail))
                .isInstanceOf(ProductUnavailableException.class);
        verify(orderRepository, never()).save(any());
        verify(orderProducer, never()).publish(any());
    }

    @Test
    void shouldGetOrderById() {
        UUID orderId = UUID.randomUUID();
        Order order = new Order(userId, userEmail, new BigDecimal("50.00"));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.getOrder(orderId, userId);

        assertThat(response.userId()).isEqualTo(userId);
    }

    @Test
    void shouldThrowWhenOrderNotFound() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrder(orderId, userId))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void shouldHideOrderOwnedBySomeoneElse() {
        UUID orderId = UUID.randomUUID();
        Order someoneElsesOrder = new Order(UUID.randomUUID(), "other@example.com", new BigDecimal("50.00"));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(someoneElsesOrder));

        // Reported as "not found", not "forbidden", so ids cannot be enumerated.
        assertThatThrownBy(() -> orderService.getOrder(orderId, userId))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void shouldGetOrderPayment() {
        UUID orderId = UUID.randomUUID();
        Order order = new Order(userId, userEmail, new BigDecimal("59.98"));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        PaymentClient.PaymentInfo info = new PaymentClient.PaymentInfo(
                UUID.randomUUID(), orderId, userId, new BigDecimal("59.98"), "APPROVED");
        when(paymentClient.getPaymentByOrder(orderId, userId)).thenReturn(info);

        PaymentClient.PaymentInfo result = orderService.getOrderPayment(orderId, userId);

        assertThat(result.status()).isEqualTo("APPROVED");
        assertThat(result.amount()).isEqualByComparingTo("59.98");
    }

    @Test
    void shouldThrowWhenGettingPaymentForUnknownOrder() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderPayment(orderId, userId))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void shouldNotCallPaymentServiceForAnotherUsersOrder() {
        UUID orderId = UUID.randomUUID();
        Order someoneElsesOrder = new Order(UUID.randomUUID(), "other@example.com", new BigDecimal("50.00"));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(someoneElsesOrder));

        assertThatThrownBy(() -> orderService.getOrderPayment(orderId, userId))
                .isInstanceOf(OrderNotFoundException.class);
        verifyNoInteractions(paymentClient);
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
        Order order = new Order(userId, userEmail, new BigDecimal("50.00"));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        orderService.confirmOrder(orderId);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(orderRepository).save(order);
    }

    @Test
    void shouldFailOrder() {
        UUID orderId = UUID.randomUUID();
        Order order = new Order(userId, userEmail, new BigDecimal("50.00"));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        orderService.failOrder(orderId);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
    }

    @Test
    void shouldIgnoreRedeliveredApprovalForAnAlreadySettledOrder() {
        UUID orderId = UUID.randomUUID();
        Order order = new Order(userId, userEmail, new BigDecimal("50.00"));
        order.setStatus(OrderStatus.CONFIRMED);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        orderService.confirmOrder(orderId);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void shouldNotLetALateFailureOverrideAConfirmedOrder() {
        UUID orderId = UUID.randomUUID();
        Order order = new Order(userId, userEmail, new BigDecimal("50.00"));
        order.setStatus(OrderStatus.CONFIRMED);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        // payment.approved and payment.failed are separate topics with no ordering guarantee.
        orderService.failOrder(orderId);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenConfirmingNonExistentOrder() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.confirmOrder(orderId))
                .isInstanceOf(OrderNotFoundException.class);
    }
}
