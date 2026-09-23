-- Transactional outbox: domain events are written here in the same transaction as the
-- aggregate that produced them, and relayed to Kafka afterwards by a poller. This removes
-- the dual write where a payment could be recorded without its PaymentApproved/PaymentFailed
-- event ever reaching order-service, leaving the order stuck in PENDING.
CREATE TABLE outbox_events (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id   UUID         NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    topic          VARCHAR(255) NOT NULL,
    payload        TEXT         NOT NULL,
    created_at     TIMESTAMP    NOT NULL DEFAULT now(),
    published_at   TIMESTAMP
);

-- The poller only ever asks for unpublished rows in insertion order; a partial index keeps
-- that scan proportional to the backlog rather than to the whole table.
CREATE INDEX idx_outbox_events_unpublished
    ON outbox_events (created_at)
    WHERE published_at IS NULL;
