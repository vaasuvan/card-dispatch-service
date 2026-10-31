ALTER TABLE card_dispatches
    ALTER COLUMN tracking_number DROP NOT NULL,
    ADD COLUMN idempotency_key_hash VARCHAR(128);

UPDATE card_dispatches SET idempotency_key_hash = dispatch_id::text;

ALTER TABLE card_dispatches
    ADD COLUMN request_fingerprint VARCHAR(64) NOT NULL DEFAULT 'legacy',
    ADD COLUMN booking_attempted_at TIMESTAMPTZ;
ALTER TABLE card_dispatches ALTER COLUMN request_fingerprint DROP DEFAULT;
ALTER TABLE card_dispatches ALTER COLUMN request_fingerprint SET NOT NULL;
ALTER TABLE card_dispatches
    ALTER COLUMN idempotency_key_hash SET NOT NULL,
    ALTER COLUMN last_updated_at SET DEFAULT CURRENT_TIMESTAMP;

CREATE UNIQUE INDEX uq_card_dispatch_customer_idempotency
    ON card_dispatches (customer_reference, idempotency_key_hash);

ALTER TABLE card_dispatches
    DROP CONSTRAINT ck_card_dispatch_status;

ALTER TABLE card_dispatches
    ADD CONSTRAINT ck_card_dispatch_status CHECK (current_status IN (
        'BOOKING_PENDING', 'BOOKING_FAILED', 'REGISTERED', 'DISPATCHED',
        'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERY_ATTEMPTED', 'DELIVERED',
        'RETURNED', 'CANCELED', 'EXCEPTION', 'UNKNOWN'
    ));

ALTER TABLE card_dispatch_events
    DROP CONSTRAINT ck_card_dispatch_event_status;

ALTER TABLE card_dispatch_events
    ADD CONSTRAINT ck_card_dispatch_event_status CHECK (status IN (
        'BOOKING_PENDING', 'BOOKING_FAILED', 'REGISTERED', 'DISPATCHED',
        'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERY_ATTEMPTED', 'DELIVERED',
        'RETURNED', 'CANCELED', 'EXCEPTION', 'UNKNOWN'
    ));
