package com.orderhub.catalog.service;

import com.orderhub.catalog.entity.StockReservation;
import com.orderhub.catalog.entity.StockReservationItem;
import com.orderhub.catalog.entity.StockReservationStatus;
import com.orderhub.catalog.event.OrderCreatedEvent;
import com.orderhub.catalog.repository.ProductRepository;
import com.orderhub.catalog.repository.StockReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The compensating-transaction side of the choreography Saga: catalog-service reserves stock
 * when {@code order.created} arrives and releases it if the order does not go through.
 *
 * <p>Today the saga only moved order status (see ARCHITECTURE.md, "Advanced" roadmap) — this
 * is the real compensating transaction that was missing.
 */
@Service
public class StockReservationService {

    private static final Logger log = LoggerFactory.getLogger(StockReservationService.class);

    private final ProductRepository productRepository;
    private final StockReservationRepository stockReservationRepository;

    public StockReservationService(ProductRepository productRepository,
                                    StockReservationRepository stockReservationRepository) {
        this.productRepository = productRepository;
        this.stockReservationRepository = stockReservationRepository;
    }

    /**
     * Kafka delivers at least once, and {@code stock_reservations.order_id} is unique, so this
     * can be called more than once for the same order. A reservation that already exists is
     * acknowledged rather than reserved again — mirrors payment-service's guard on
     * {@code payments.order_id}.
     *
     * <p>Each line is decremented with a single conditional {@code UPDATE ... WHERE stock >=
     * quantity} (see {@link ProductRepository#decrementStock}): the availability check and the
     * write are one atomic statement, so there is no read-modify-write race between concurrent
     * orders for the same product. If a later line turns out short, the lines already
     * decremented for this order are put back before the reservation is recorded — the whole
     * order is reserved or none of it is.
     *
     * <p>Insufficient stock does not throw. A full implementation would publish
     * {@code stock.rejected} so order-service could settle the order (documented as future
     * work in ARCHITECTURE.md); until that exists, throwing here would only retry the same
     * failure three times and dead-letter it for no benefit, so this logs a warning and moves
     * on with a FAILED reservation recorded for inspection.
     */
    @Transactional
    public void reserveStock(OrderCreatedEvent event) {
        if (stockReservationRepository.findByOrderId(event.orderId()).isPresent()) {
            log.info("Stock reservation already exists for order {}, ignoring duplicate event", event.orderId());
            return;
        }

        List<OrderCreatedEvent.OrderItemEvent> applied = new ArrayList<>();
        for (OrderCreatedEvent.OrderItemEvent item : event.items()) {
            int updated = productRepository.decrementStock(item.productId(), item.quantity());
            if (updated == 0) {
                applied.forEach(a -> productRepository.incrementStock(a.productId(), a.quantity()));
                log.warn("Insufficient stock for product {} (order {}), recording FAILED reservation",
                        item.productId(), event.orderId());
                saveReservation(event, StockReservationStatus.FAILED);
                return;
            }
            applied.add(item);
        }

        saveReservation(event, StockReservationStatus.RESERVED);
        log.info("Reserved stock for order {}", event.orderId());
    }

    /**
     * Releases a reservation on the saga's compensating path. Both {@code payment.failed} and
     * the order-service {@code order.cancelled} event call this for the same order, and Kafka
     * can redeliver either one. {@link StockReservationRepository#releaseIfReserved} is the
     * atomic RESERVED -> RELEASED transition; only the caller that actually performs it puts
     * stock back, so a redelivery or the other trigger arriving afterwards is a no-op.
     */
    @Transactional
    public void releaseReservation(UUID orderId, String trigger) {
        StockReservation reservation = stockReservationRepository.findByOrderId(orderId).orElse(null);
        if (reservation == null) {
            log.info("No stock reservation found for order {}, skipping release ({})", orderId, trigger);
            return;
        }

        if (stockReservationRepository.releaseIfReserved(orderId) == 0) {
            log.info("Reservation for order {} is already {}, skipping release ({})",
                    orderId, reservation.getStatus(), trigger);
            return;
        }

        reservation.getItems().forEach(item ->
                productRepository.incrementStock(item.getProductId(), item.getQuantity()));
        log.info("Released stock reservation for order {} ({})", orderId, trigger);
    }

    private void saveReservation(OrderCreatedEvent event, StockReservationStatus status) {
        StockReservation reservation = new StockReservation(event.orderId(), status);
        event.items().forEach(item ->
                reservation.addItem(new StockReservationItem(reservation, item.productId(), item.quantity())));
        stockReservationRepository.save(reservation);
    }
}
