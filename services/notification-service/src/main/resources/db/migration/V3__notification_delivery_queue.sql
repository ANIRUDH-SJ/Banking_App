ALTER TABLE notification_delivery DROP CONSTRAINT ck_notification_delivery_status;

ALTER TABLE notification_delivery ADD (
    recipient       VARCHAR2(254 CHAR),
    attempt_count   NUMBER(10) DEFAULT 0 NOT NULL,
    next_attempt_at TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    delivered_at    TIMESTAMP(6)
);

ALTER TABLE notification_delivery ADD CONSTRAINT ck_notification_delivery_status
    CHECK (delivery_status IN ('PENDING', 'PROCESSING', 'SENT', 'FAILED'));

ALTER TABLE notification_delivery ADD CONSTRAINT uq_notification_delivery_channel
    UNIQUE (notification_id, channel);

CREATE INDEX ix_notification_delivery_pending
    ON notification_delivery(delivery_status, next_attempt_at);
