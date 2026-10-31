CREATE TABLE card_dispatches
(
    dispatch_id             UUID PRIMARY KEY,
    customer_reference      VARCHAR(128) NOT NULL,
    partner_code            VARCHAR(63)  NOT NULL,
    tracking_number         VARCHAR(256) NOT NULL,
    dispatched_at           TIMESTAMPTZ  NOT NULL,
    current_status          VARCHAR(32)  NOT NULL DEFAULT 'REGISTERED',
    status_description      TEXT,
    estimated_delivery_date DATE,
    last_updated_at         TIMESTAMPTZ,
    refreshed_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_card_dispatch_status CHECK (current_status IN (
                                                                 'REGISTERED', 'DISPATCHED', 'IN_TRANSIT',
                                                                 'OUT_FOR_DELIVERY',
                                                                 'DELIVERY_ATTEMPTED', 'DELIVERED', 'RETURNED',
                                                                 'EXCEPTION', 'UNKNOWN'
        ))
);

CREATE INDEX ix_card_dispatch_partner ON card_dispatches (partner_code);
CREATE INDEX ix_card_dispatch_refresh ON card_dispatches (refreshed_at);

CREATE TABLE card_dispatch_events
(
    event_id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    dispatch_id UUID        NOT NULL REFERENCES card_dispatches (dispatch_id) ON DELETE CASCADE,
    status      VARCHAR(32) NOT NULL,
    description TEXT,
    occurred_at TIMESTAMPTZ NOT NULL,
    location    TEXT,
    CONSTRAINT ck_card_dispatch_event_status CHECK (status IN (
                                                               'REGISTERED', 'DISPATCHED', 'IN_TRANSIT',
                                                               'OUT_FOR_DELIVERY',
                                                               'DELIVERY_ATTEMPTED', 'DELIVERED', 'RETURNED',
                                                               'EXCEPTION', 'UNKNOWN'
        ))
);

CREATE INDEX ix_card_dispatch_events_dispatch_time
    ON card_dispatch_events (dispatch_id, occurred_at DESC);
