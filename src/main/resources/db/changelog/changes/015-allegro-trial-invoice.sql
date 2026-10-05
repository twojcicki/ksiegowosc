--liquibase formatted sql

--changeset ksiegowosc:015-allegro-trial-invoice
CREATE TABLE allegro_trial_invoice (
    id BIGSERIAL NOT NULL,
    user_id BIGINT NOT NULL,
    account_id BIGINT,
    order_id VARCHAR(64) NOT NULL,
    payload_json TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_allegro_trial_invoice PRIMARY KEY (id),
    CONSTRAINT uk_allegro_trial_invoice_order UNIQUE (order_id),
    CONSTRAINT fk_allegro_trial_invoice_user FOREIGN KEY (user_id) REFERENCES app_user (id)
);

CREATE INDEX idx_allegro_trial_invoice_user_created ON allegro_trial_invoice (user_id, created_at DESC);
