--liquibase formatted sql

--changeset ksiegowosc:014-user-invoice-schedule
CREATE TABLE user_invoice_schedule (
    user_id BIGINT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    interval_minutes INT NOT NULL DEFAULT 15,
    invoices_from_date DATE,
    live_mode BOOLEAN NOT NULL DEFAULT FALSE,
    last_run_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_user_invoice_schedule PRIMARY KEY (user_id),
    CONSTRAINT fk_user_invoice_schedule_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT ck_user_invoice_schedule_interval CHECK (interval_minutes >= 1 AND interval_minutes <= 60)
);
