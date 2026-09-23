package com.orderhub.catalog.service;

import com.orderhub.catalog.entity.StockReservation;
import com.orderhub.catalog.entity.StockReservationItem;
import com.orderhub.catalog.entity.StockReservationStatus;
import com.orderhub.catalog.event.OrderCreatedEvent;
import com.orderhub.catalog.repository.ProductRepository;
import com.orderhub.catalog.repository.StockReservationRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("StockReservationService")
class StockReservationServiceTest {

    @Mock ProductRepository productRepository;
    @Mock StockReservationRepository stockReservationRepository;
    @Spy MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @InjectMocks StockReservationService stockReservationService;

    private UUID orderId;
    private UUID productA;
    private UUID productB;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        productA = UUID.randomUUID();
        productB = UUID.randomUUID();
    }

    private OrderCreatedEvent.OrderItemEvent item(UUID productId, int quantity) {
        return new OrderCreatedEvent.OrderItemEvent(productId, "Item", new BigDecimal("10.00"), quantity);
    }

    private OrderCreatedEvent eventFor(OrderCreatedEvent.OrderItemEvent... items) {
        return new OrderCreatedEvent(orderId, UUID.randomUUID(), "user@example.com",
                List.of(items), new BigDecimal("20.00"), LocalDateTime.now());
    }

    @Nested
    @DisplayName("when an OrderCreated event arrives")
    class ReservingStock {

        @Test
        @DisplayName("reserves stock for every line and records a RESERVED reservation")
        void shouldReserveStockForEveryLine() {
            when(stockReservationRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
            when(productRepository.decrementStock(any(), anyInt())).thenReturn(1);

            stockReservationService.reserveStock(eventFor(item(productA, 2), item(productB, 3)));

            verify(productRepository).decrementStock(productA, 2);
            verify(productRepository).decrementStock(productB, 3);
            verify(productRepository, never()).incrementStock(any(), anyInt());

            ArgumentCaptor<StockReservation> captor = ArgumentCaptor.forClass(StockReservation.class);
            verify(stockReservationRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(StockReservationStatus.RESERVED);
            assertThat(captor.getValue().getOrderId()).isEqualTo(orderId);
            assertThat(captor.getValue().getItems()).hasSize(2);
        }

        @Test
        @DisplayName("puts back already-decremented lines and records FAILED when a later line is short")
        void shouldRevertAndRecordFailedOnInsufficientStock() {
            when(stockReservationRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
            when(productRepository.decrementStock(eq(productA), anyInt())).thenReturn(1);
            when(productRepository.decrementStock(eq(productB), anyInt())).thenReturn(0);

            // Must not throw: a full implementation would publish stock.rejected, not
            // implemented yet — this only logs and records the failure.
            stockReservationService.reserveStock(eventFor(item(productA, 2), item(productB, 100)));

            verify(productRepository).incrementStock(productA, 2);

            ArgumentCaptor<StockReservation> captor = ArgumentCaptor.forClass(StockReservation.class);
            verify(stockReservationRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(StockReservationStatus.FAILED);
            assertThat(meterRegistry.counter("orderhub.stock.reservations.failed").count())
                    .isEqualTo(1.0);
        }

        @Test
        @DisplayName("ignores a redelivered order.created for an order already reserved")
        void shouldIgnoreDuplicateOrderCreatedEvents() {
            when(stockReservationRepository.findByOrderId(orderId))
                    .thenReturn(Optional.of(new StockReservation(orderId, StockReservationStatus.RESERVED)));

            stockReservationService.reserveStock(eventFor(item(productA, 2)));

            verify(productRepository, never()).decrementStock(any(), anyInt());
            verify(stockReservationRepository, never()).save(any());
        }

        @Test
        @DisplayName("does not retry a FAILED reservation on redelivery — FAILED is terminal")
        void shouldNotRetryAFailedReservationOnRedelivery() {
            when(stockReservationRepository.findByOrderId(orderId))
                    .thenReturn(Optional.of(new StockReservation(orderId, StockReservationStatus.FAILED)));

            // Even if stock was replenished meanwhile, the existence guard wins: the
            // reservation stays FAILED until a stock.rejected flow exists to settle it.
            stockReservationService.reserveStock(eventFor(item(productA, 2)));

            verify(productRepository, never()).decrementStock(any(), anyInt());
            verify(stockReservationRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("when releasing a reservation")
    class ReleasingStock {

        private StockReservation reservationWith(StockReservationStatus status, UUID productId, int quantity) {
            StockReservation reservation = new StockReservation(orderId, status);
            reservation.addItem(new StockReservationItem(reservation, productId, quantity));
            return reservation;
        }

        @Test
        @DisplayName("puts stock back on payment.failed")
        void shouldReleaseOnPaymentFailed() {
            when(stockReservationRepository.findByOrderId(orderId))
                    .thenReturn(Optional.of(reservationWith(StockReservationStatus.RESERVED, productA, 4)));
            when(stockReservationRepository.releaseIfReserved(orderId)).thenReturn(1);

            stockReservationService.releaseReservation(orderId, "payment.failed");

            verify(productRepository).incrementStock(productA, 4);
        }

        @Test
        @DisplayName("puts stock back on order.cancelled")
        void shouldReleaseOnOrderCancelled() {
            when(stockReservationRepository.findByOrderId(orderId))
                    .thenReturn(Optional.of(reservationWith(StockReservationStatus.RESERVED, productA, 4)));
            when(stockReservationRepository.releaseIfReserved(orderId)).thenReturn(1);

            stockReservationService.releaseReservation(orderId, "order.cancelled");

            verify(productRepository).incrementStock(productA, 4);
        }

        @Test
        @DisplayName("is a no-op when no reservation exists for the order")
        void shouldSkipWhenNoReservationExists() {
            when(stockReservationRepository.findByOrderId(orderId)).thenReturn(Optional.empty());

            stockReservationService.releaseReservation(orderId, "payment.failed");

            verify(stockReservationRepository, never()).releaseIfReserved(any());
            verify(productRepository, never()).incrementStock(any(), anyInt());
        }

        /**
         * payment.failed and order.cancelled can both arrive for the same order (and either
         * can be redelivered). The conditional UPDATE only lets the first release through —
         * every subsequent call, from any trigger, must see zero rows updated and skip.
         */
        @Test
        @DisplayName("does not release stock twice, regardless of which trigger arrives second")
        void shouldNotReleaseTwice() {
            when(stockReservationRepository.findByOrderId(orderId))
                    .thenReturn(Optional.of(reservationWith(StockReservationStatus.RELEASED, productA, 4)));
            when(stockReservationRepository.releaseIfReserved(orderId)).thenReturn(0);

            stockReservationService.releaseReservation(orderId, "order.cancelled");

            verify(productRepository, never()).incrementStock(any(), anyInt());
        }

        @Test
        @DisplayName("skips a reservation that already failed at reservation time")
        void shouldSkipAnAlreadyFailedReservation() {
            when(stockReservationRepository.findByOrderId(orderId))
                    .thenReturn(Optional.of(reservationWith(StockReservationStatus.FAILED, productA, 4)));
            when(stockReservationRepository.releaseIfReserved(orderId)).thenReturn(0);

            stockReservationService.releaseReservation(orderId, "payment.failed");

            verify(productRepository, never()).incrementStock(any(), anyInt());
        }
    }
}
