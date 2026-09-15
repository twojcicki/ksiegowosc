--liquibase formatted sql

--changeset ksiegowosc:009-drop-allegro-invoice-sequence
DROP TABLE allegro_invoice_sequence;
