package com.orderhub.order.service;

import com.orderhub.common.outbox.OutboxRecorder;
import com.orderhub.order.client.CatalogClient;
import com.orderhub.order.client.PaymentClient;
import com.orderhub.order.dto.CreateOrderRequest;
import com.orderhub.order.dto.OrderItemRequest;
import com.orderhub.order.dto.OrderResponse;
import com.orderhub.order.entity.Order;
import com.orderhub.order.entity.OrderStatus;
import com.orderhub.order.event.OrderCreatedEvent;
import com.orderhub.order.exception.OrderNotFoundException;
import com.orderhub.order.exception.ProductUnavailableException;
import com.orderhub.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService")
class OrderServiceTest {

    @Mock OrderRepository orderRepository;
    @Mock OutboxRecorder outboxRecorder;
    @Mock CatalogClient catalogClient;
    @Mock PaymentClient paymentClient;

    @InjectMocks OrderService orderService;

    private UUID userId;
    private UUID orderId;
    private String userEmail;
    private UUID productId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        userEmail = "user@example.com";
        productId = UUID.randomUUID();
    }

    // ---------------------------------------------------------------- helpers

    private Order orderOwnedBy(UUID owner) {
        return new Order(owner, "owner@example.com", new BigDecimal("50.00"));
    }

    private void givenStoredOrder(Order order) {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
    }

    private void givenNoStoredOrder() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());
    }

    private void givenCatalogOffers(String name, String price, boolean available) {
        when(catalogClient.getProduct(productId)).thenReturn(
                new CatalogClient.ProductResponse(productId, name, new BigDecimal(price), available));
    }

    private CreateOrderRequest requestFor(int quantity) {
        return new CreateOrderRequest(List.of(new OrderItemRequest(productId, quantity)));
    }

    // ---------------------------------------------------------------- tests

    @Nested
    @DisplayName("when placing an order")
    class PlacingAnOrder {

        @Test
        @DisplayName("prices the order from the catalog, never from the client")
        void shouldPriceFromTheCatalog() {
            givenCatalogOffers("Product A", "29.99", true);
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            OrderResponse response = orderService.createOrder(requestFor(2), userId, userEmail);

            // A client that posts its own price must not be able to influence the total.
            assertThat(response.totalAmount()).isEqualByComparingTo(new BigDecimal("59.98"));
            assertThat(response.items()).singleElement()
                    .satisfies(item -> assertThat(item.price()).isEqualByComparingTo(new BigDecimal("29.99")));
        }

        @Test
        @DisplayName("stages OrderCreated in the outbox instead of sending it to Kafka")
        void shouldStageTheEventInTheOutbox() {
            givenCatalogOffers("Product A", "29.99", true);
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            orderService.createOrder(requestFor(2), userId, userEmail);

            ArgumentCaptor<OrderCreatedEvent> captor = ArgumentCaptor.forClass(OrderCreatedEvent.class);
            verify(outboxRecorder).record(captor.capture());
            OrderCreatedEvent staged = captor.getValue();
            assertThat(staged.topic()).isEqualTo("order.created");
            assertThat(staged.totalAmount()).isEqualByComparingTo(new BigDecimal("59.98"));
        }

        @Test
        @DisplayName("rejects an unavailable product without saving or staging anything")
        void shouldRejectAnUnavailableProduct() {
            givenCatalogOffers("Product A", "29.99", false);

            assertThatThrownBy(() -> orderService.createOrder(requestFor(1), userId, userEmail))
                    .isInstanceOf(ProductUnavailableException.class);

            // Nothing may be left behind: a staged event for a rolled-back order would start
            // a Saga for something that never existed.
            verify(orderRepository, never()).save(any());
            verifyNoInteractions(outboxRecorder);
        }
    }

    @Nested
    @DisplayName("when reading an order")
    class ReadingAnOrder {

        @Test
        @DisplayName("returns the order to its owner")
        void shouldReturnTheOrderToItsOwner() {
            givenStoredOrder(orderOwnedBy(userId));

            assertThat(orderService.getOrder(orderId, userId).userId()).isEqualTo(userId);
        }

        @Test
        @DisplayName("reports an unknown order as not found")
        void shouldReportUnknownOrderAsNotFound() {
            givenNoStoredOrder();

            assertThatThrownBy(() -> orderService.getOrder(orderId, userId))
                    .isInstanceOf(OrderNotFoundException.class);
        }

        @Test
        @DisplayName("reports someone else's order as not found, not forbidden")
        void shouldHideAnotherUsersOrder() {
            givenStoredOrder(orderOwnedBy(UUID.randomUUID()));

            // 404 rather than 403: a 403 would confirm the id exists, turning the endpoint
            // into an enumeration oracle (OWASP API1).
            assertThatThrownBy(() -> orderService.getOrder(orderId, userId))
                    .isInstanceOf(OrderNotFoundException.class);
        }

        @Test
        @DisplayName("lists only the caller's own orders")
        void shouldListTheCallersOrders() {
            when(orderRepository.findByUserId(userId)).thenReturn(
                    List.of(orderOwnedBy(userId), orderOwnedBy(userId)));

            assertThat(orderService.getOrdersByUser(userId)).hasSize(2);
        }
    }

    @Nested
    @DisplayName("when reading an order's payment")
    class ReadingAPayment {

        @Test
        @DisplayName("fetches it from payment-service for the owner")
        void shouldFetchThePaymentForTheOwner() {
            givenStoredOrder(orderOwnedBy(userId));
            when(paymentClient.getPaymentByOrder(orderId, userId)).thenReturn(
                    new PaymentClient.PaymentInfo(
                            UUID.randomUUID(), orderId, userId, new BigDecimal("59.98"), "APPROVED"));

            PaymentClient.PaymentInfo result = orderService.getOrderPayment(orderId, userId);

            assertThat(result.status()).isEqualTo("APPROVED");
        }

        @Test
        @DisplayName("reports an unknown order as not found")
        void shouldReportUnknownOrderAsNotFound() {
            givenNoStoredOrder();

            assertThatThrownBy(() -> orderService.getOrderPayment(orderId, userId))
                    .isInstanceOf(OrderNotFoundException.class);
        }

        @Test
        @DisplayName("does not call payment-service for someone else's order")
        void shouldNotCallPaymentServiceForAnotherUsersOrder() {
            givenStoredOrder(orderOwnedBy(UUID.randomUUID()));

            assertThatThrownBy(() -> orderService.getOrderPayment(orderId, userId))
                    .isInstanceOf(OrderNotFoundException.class);

            // The ownership check must happen before the call, not after: otherwise the
            // request still leaks that the order exists, and costs a downstream round trip.
            verifyNoInteractions(paymentClient);
        }
    }

    @Nested
    @DisplayName("when a payment outcome arrives")
    class ApplyingASagaOutcome {

        @Test
        @DisplayName("confirms a pending order")
        void shouldConfirmAPendingOrder() {
            Order order = orderOwnedBy(userId);
            givenStoredOrder(order);

            orderService.confirmOrder(orderId);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            verify(orderRepository).save(order);
        }

        @Test
        @DisplayName("fails a pending order")
        void shouldFailAPendingOrder() {
            Order order = orderOwnedBy(userId);
            givenStoredOrder(order);

            orderService.failOrder(orderId);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        }

        /**
         * Kafka is at-least-once and gives no ordering guarantee <em>between</em> the
         * payment.approved and payment.failed topics. Whatever an order has already settled
         * as, no redelivered or late event may move it — so this is checked for every
         * non-pending status rather than just the one that happened to be written first.
         */
        @ParameterizedTest(name = "leaves an order that is already {0} untouched")
        @EnumSource(value = OrderStatus.class, names = "PENDING", mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("ignores an outcome for an order that already settled")
        void shouldIgnoreOutcomesForSettledOrders(OrderStatus settled) {
            Order order = orderOwnedBy(userId);
            order.setStatus(settled);
            givenStoredOrder(order);

            orderService.confirmOrder(orderId);
            orderService.failOrder(orderId);

            assertThat(order.getStatus()).isEqualTo(settled);
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("reports an outcome for an unknown order as not found")
        void shouldReportUnknownOrderAsNotFound() {
            givenNoStoredOrder();

            assertThatThrownBy(() -> orderService.confirmOrder(orderId))
                    .isInstanceOf(OrderNotFoundException.class);
        }
    }
}
