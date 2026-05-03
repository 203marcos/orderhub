CREATE TABLE payments (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id       UUID           NOT NULL UNIQUE,
    user_id        UUID           NOT NULL,
    user_email     VARCHAR(255)   NOT NULL,
    amount         NUMERIC(12, 2) NOT NULL,
    status         VARCHAR(50)    NOT NULL DEFAULT 'PROCESSING',
    failure_reason VARCHAR(500),
    created_at     TIMESTAMP      NOT NULL DEFAULT now(),
    processed_at   TIMESTAMP      NOT NULL DEFAULT now()
);

CREATE INDEX idx_payments_order_id ON payments(order_id);
CREATE INDEX idx_payments_user_id ON payments(user_id);
