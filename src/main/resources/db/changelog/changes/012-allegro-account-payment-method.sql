--liquibase formatted sql

--changeset ksiegowosc:012-allegro-account-payment-method
-- Existing accounts keep NULL until set in UI; new/updated accounts require payment_method.
ALTER TABLE allegro_account ADD COLUMN payment_method VARCHAR(100);
