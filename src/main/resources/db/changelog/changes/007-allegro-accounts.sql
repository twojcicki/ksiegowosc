--liquibase formatted sql

--changeset ksiegowosc:007-allegro-accounts
CREATE TABLE allegro_account (
    id BIGSERIAL NOT NULL,
    user_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    client_id VARCHAR(200) NOT NULL,
    client_secret VARCHAR(500) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_allegro_account PRIMARY KEY (id),
    CONSTRAINT fk_allegro_account_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT uk_allegro_account_user_client UNIQUE (user_id, client_id)
);

INSERT INTO allegro_account (user_id, name, client_id, client_secret, created_at, updated_at)
SELECT user_id,
       CASE
           WHEN allegro_client_id IS NULL OR TRIM(allegro_client_id) = '' THEN 'Allegro'
           ELSE allegro_client_id
       END,
       allegro_client_id,
       allegro_client_secret,
       updated_at,
       updated_at
FROM user_api_credentials
WHERE allegro_client_id IS NOT NULL
  AND TRIM(allegro_client_id) <> ''
  AND allegro_client_secret IS NOT NULL
  AND TRIM(allegro_client_secret) <> '';

CREATE TABLE allegro_token_new (
    account_id BIGINT NOT NULL,
    access_token VARCHAR(8192) NOT NULL,
    refresh_token VARCHAR(8192) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_allegro_token_new PRIMARY KEY (account_id),
    CONSTRAINT fk_allegro_token_account FOREIGN KEY (account_id) REFERENCES allegro_account (id) ON DELETE CASCADE
);

INSERT INTO allegro_token_new (account_id, access_token, refresh_token, expires_at, updated_at)
SELECT a.id, t.access_token, t.refresh_token, t.expires_at, t.updated_at
FROM allegro_token t
INNER JOIN allegro_account a ON a.user_id = t.user_id;

DROP TABLE allegro_token;

ALTER TABLE allegro_token_new RENAME TO allegro_token;

ALTER TABLE user_api_credentials DROP COLUMN allegro_client_id;
ALTER TABLE user_api_credentials DROP COLUMN allegro_client_secret;
