--liquibase formatted sql

--changeset ksiegowosc:013-allegro-sold-invoice-issue-error
ALTER TABLE allegro_sold_invoice ALTER COLUMN invoice_no DROP NOT NULL;
ALTER TABLE allegro_sold_invoice ADD COLUMN issue_error TEXT;
ALTER TABLE allegro_sold_invoice ADD COLUMN issue_error_at TIMESTAMP WITH TIME ZONE;
