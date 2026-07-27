-- The compensating side of the choreography Saga: catalog-service reserves stock when
-- order.created arrives and releases it if the order does not go through (payment.failed or
-- order.cancelled). order_id is unique so a redelivered order.created can never reserve the
-- same order's stock twice — mirrors payments.order_id in payment-service.
CREATE TABLE stock_reservations (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id   UUID        NOT NULL UNIQUE,
    status     VARCHAR(20) NOT NULL,
    created_at TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at TIMESTAMP   NOT NULL DEFAULT now()
);

-- One row per order line, so a release can put back exactly what was reserved per product.
CREATE TABLE stock_reservation_items (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    stock_reservation_id UUID    NOT NULL REFERENCES stock_reservations(id) ON DELETE CASCADE,
    product_id           UUID    NOT NULL,
    quantity             INTEGER NOT NULL
);

CREATE INDEX idx_stock_reservation_items_reservation_id ON stock_reservation_items(stock_reservation_id);
