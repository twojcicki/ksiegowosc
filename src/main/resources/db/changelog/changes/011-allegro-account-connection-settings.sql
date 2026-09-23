--liquibase formatted sql

--changeset ksiegowosc:011-allegro-account-connection-settings
-- Existing accounts keep sandbox hosts (previous global defaults). New accounts set URLs in UI.
ALTER TABLE allegro_account ADD COLUMN api_base_url VARCHAR(300) DEFAULT 'https://api.allegro.pl.allegrosandbox.pl';
ALTER TABLE allegro_account ADD COLUMN auth_url VARCHAR(300) DEFAULT 'https://allegro.pl.allegrosandbox.pl';
ALTER TABLE allegro_account ADD COLUMN user_agent VARCHAR(500) DEFAULT 'Ksiegowosc/0.0.1 (+https://ksiegowosc-a0yu.onrender.com)';

UPDATE allegro_account SET api_base_url = 'https://api.allegro.pl.allegrosandbox.pl' WHERE api_base_url IS NULL;
UPDATE allegro_account SET auth_url = 'https://allegro.pl.allegrosandbox.pl' WHERE auth_url IS NULL;
UPDATE allegro_account SET user_agent = 'Ksiegowosc/0.0.1 (+https://ksiegowosc-a0yu.onrender.com)' WHERE user_agent IS NULL;

ALTER TABLE allegro_account ALTER COLUMN api_base_url SET NOT NULL;
ALTER TABLE allegro_account ALTER COLUMN auth_url SET NOT NULL;
ALTER TABLE allegro_account ALTER COLUMN user_agent SET NOT NULL;
