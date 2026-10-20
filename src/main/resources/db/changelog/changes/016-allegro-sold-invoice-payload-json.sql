--liquibase formatted sql

--changeset ksiegowosc:016-allegro-sold-invoice-payload-json
ALTER TABLE allegro_sold_invoice ADD COLUMN payload_json TEXT;
