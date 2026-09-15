--liquibase formatted sql

--changeset ksiegowosc:008-allegro-invoice-prefix
ALTER TABLE allegro_account
    ADD COLUMN invoice_prefix VARCHAR(20) NOT NULL DEFAULT '';

CREATE TABLE allegro_invoice_sequence (
    account_id BIGINT NOT NULL,
    year_value INT NOT NULL,
    month_value INT NOT NULL,
    last_number INT NOT NULL,
    CONSTRAINT pk_allegro_invoice_sequence PRIMARY KEY (account_id, year_value, month_value),
    CONSTRAINT fk_allegro_invoice_sequence_account FOREIGN KEY (account_id)
        REFERENCES allegro_account (id) ON DELETE CASCADE,
    CONSTRAINT ck_allegro_invoice_sequence_month CHECK (month_value BETWEEN 1 AND 12),
    CONSTRAINT ck_allegro_invoice_sequence_number CHECK (last_number >= 0)
);
