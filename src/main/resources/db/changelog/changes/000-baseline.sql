--liquibase formatted sql

--changeset ksiegowosc:000-baseline
CREATE TABLE invoice_email_status (
    invoice_id VARCHAR(64) NOT NULL,
    email_sent BOOLEAN NOT NULL DEFAULT FALSE,
    email_sent_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_invoice_email_status PRIMARY KEY (invoice_id)
);

CREATE TABLE app_user (
    id BIGSERIAL NOT NULL,
    login VARCHAR(100) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    CONSTRAINT pk_app_user PRIMARY KEY (id),
    CONSTRAINT uk_app_user_login UNIQUE (login)
);

CREATE TABLE user_api_credentials (
    user_id BIGINT NOT NULL,
    merit_api_id VARCHAR(100),
    merit_api_key VARCHAR(500),
    allegro_client_id VARCHAR(200),
    allegro_client_secret VARCHAR(500),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_user_api_credentials PRIMARY KEY (user_id),
    CONSTRAINT fk_user_api_credentials_user FOREIGN KEY (user_id) REFERENCES app_user (id)
);

CREATE TABLE allegro_token (
    user_id BIGINT NOT NULL,
    access_token VARCHAR(8192) NOT NULL,
    refresh_token VARCHAR(8192) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_allegro_token PRIMARY KEY (user_id),
    CONSTRAINT fk_allegro_token_user FOREIGN KEY (user_id) REFERENCES app_user (id)
);

CREATE TABLE allegro_sold_invoice (
    order_id VARCHAR(64) NOT NULL,
    invoice_no VARCHAR(35) NOT NULL,
    merit_invoice_id VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_allegro_sold_invoice PRIMARY KEY (order_id),
    CONSTRAINT uk_allegro_sold_invoice_invoice_no UNIQUE (invoice_no)
);
