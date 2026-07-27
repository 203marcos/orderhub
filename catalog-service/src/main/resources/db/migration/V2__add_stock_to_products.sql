-- Stock backs the reservation saga: order-service's order.created is the trigger for
-- catalog-service to reserve stock atomically (see V3, stock_reservations). Existing rows
-- default to 0 rather than NULL, so the reservation math (stock - quantity) never has to
-- special-case an unset product.
ALTER TABLE products
    ADD COLUMN stock INTEGER NOT NULL DEFAULT 0;
