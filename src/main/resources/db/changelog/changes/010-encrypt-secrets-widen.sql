--liquibase formatted sql

--changeset ksiegowosc:010-encrypt-secrets-widen-postgresql dbms:postgresql
ALTER TABLE user_api_credentials ALTER COLUMN merit_api_key TYPE VARCHAR(2000);
ALTER TABLE allegro_account ALTER COLUMN client_secret TYPE VARCHAR(2000);
ALTER TABLE allegro_token ALTER COLUMN access_token TYPE VARCHAR(16384);
ALTER TABLE allegro_token ALTER COLUMN refresh_token TYPE VARCHAR(16384);

--changeset ksiegowosc:010-encrypt-secrets-widen-h2 dbms:h2
ALTER TABLE user_api_credentials ALTER COLUMN merit_api_key SET DATA TYPE VARCHAR(2000);
ALTER TABLE allegro_account ALTER COLUMN client_secret SET DATA TYPE VARCHAR(2000);
ALTER TABLE allegro_token ALTER COLUMN access_token SET DATA TYPE VARCHAR(16384);
ALTER TABLE allegro_token ALTER COLUMN refresh_token SET DATA TYPE VARCHAR(16384);
