CREATE TABLE orders (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID        NOT NULL,
    user_email   VARCHAR(255) NOT NULL,
    status       VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    total_amount NUMERIC(12, 2) NOT NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE order_items (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id     UUID           NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id   UUID           NOT NULL,
    product_name VARCHAR(255)   NOT NULL,
    price        NUMERIC(12, 2) NOT NULL,
    quantity     INTEGER        NOT NULL
);

CREATE INDEX idx_orders_user_id ON orders(user_id);
CREATE INDEX idx_order_items_order_id ON order_items(order_id);
