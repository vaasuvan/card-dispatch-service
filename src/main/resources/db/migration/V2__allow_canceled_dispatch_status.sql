ALTER TABLE card_dispatches
DROP
CONSTRAINT ck_card_dispatch_status;

ALTER TABLE card_dispatches
    ADD CONSTRAINT ck_card_dispatch_status CHECK (current_status IN (
                                                                     'REGISTERED', 'DISPATCHED', 'IN_TRANSIT',
                                                                     'OUT_FOR_DELIVERY',
                                                                     'DELIVERY_ATTEMPTED', 'DELIVERED', 'RETURNED',
                                                                     'CANCELED', 'EXCEPTION', 'UNKNOWN'
        ));

ALTER TABLE card_dispatch_events
DROP
CONSTRAINT ck_card_dispatch_event_status;

ALTER TABLE card_dispatch_events
    ADD CONSTRAINT ck_card_dispatch_event_status CHECK (status IN (
                                                                   'REGISTERED', 'DISPATCHED', 'IN_TRANSIT',
                                                                   'OUT_FOR_DELIVERY',
                                                                   'DELIVERY_ATTEMPTED', 'DELIVERED', 'RETURNED',
                                                                   'CANCELED', 'EXCEPTION', 'UNKNOWN'
        ));
