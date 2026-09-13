# Ustawienia API powiązane z użytkownikiem

## Zakres

- Formularz `/ustawienia-api`: Merit Api Id/Key, Allegro Client ID/Secret
- Zapis w `user_api_credentials` (PK = `app_user.id`)
- Tokeny OAuth Allegro per użytkownik (`allegro_token.user_id`)
- Przycisk **Usuń powiązanie** → `DELETE` tokena OAuth
- Bez zmiennych `MERIT_*` / `ALLEGRO_CLIENT_*` (URL-e i redirect URI w yml)

## Runtime

- `CurrentUserApiCredentialsService` — odczyt/zapis credentials bieżącego usera
- `MeritAuthInterceptor` — podpis HMAC z kluczy użytkownika
- `AllegroAuthService` — client id/secret z DB; OAuth `state` = userId; `disconnect()`

## Migracja

Liquibase `006-user-api-credentials.yaml`: tabela credentials + przebudowa `allegro_token` (stary singleton `default` usuwany).
