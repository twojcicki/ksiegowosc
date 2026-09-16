# Changelog

Format oparty na [Keep a Changelog](https://keepachangelog.com/pl/1.1.0/).

## [Unreleased]

### Added

- Szyfrowanie at-rest sekretów API (AES-256-GCM): `merit_api_key`, Allegro `client_secret`, OAuth `access_token` / `refresh_token`; klucz `APP_ENCRYPTION_KEY` (Base64, 32 bajty).
- Zmiana hasła z menu użytkownika (obecne + nowe + potwierdzenie, min. 8 znaków).

### Changed

- Logi Merit API: na INFO tylko metadane (method, URI, status, rozmiary, czas); skrócony body (2 KB) wyłącznie przy 4xx/5xx na WARN.

### Fixed

- Login bez bocznego menu: `@Route(autoLayout = false)` — widok logowania poza `MainLayout`.
- OAuth Allegro: dostęp do `/api/**` dla zalogowanych użytkowników (Vaadin `denyAll` dawał 403 na „Połącz z Allegro”).
- OAuth Allegro: auto Redirect URI z hosta (bez `localhost` na Render) + podpowiedź URI w Ustawieniach API.

### Added

- Faktura Allegro obejmuje koszty kupującego: `delivery.cost`, `surcharges`, usługi dodatkowe; brutto = `summary.totalToPay` (±0,01); dostawa: `Item.Code=method.id`, VAT 23%, `Type=2`.
- Zakładka Allegro „Mapowanie” z podzakładkami Reguły (`AllegroMeritInvoiceMappings.RULES`) i Podgląd wartości Merit (w tym `InvoiceRow[].Item.*`) bez `sendinvoice`.
- Prefiks faktury per konto Allegro; numer Merit: `prefiks/kolejny/MM/rrrr` (np. `FS/5/09/2026`), kolejny numer = liczba faktur w Merit w miesiącu dokumentu + 1.
- Wiele kont Allegro per użytkownik (`allegro_account`): nazwa, Client ID/Secret (szyfrowany at-rest), Połącz/Usuń; oferty i sprzedane ze wszystkich połączonych kont z kolumną „Konto”.
- Ustawienia API per użytkownik (Merit Api Id/Key w `user_api_credentials`).
  Notatka: [docs/plans/user-api-settings.md](docs/plans/user-api-settings.md)

### Changed

- Liquibase: historia YAML 001–006 zwinięta do `000-baseline.sql`; kolejne migracje tylko jako formatted SQL (`007-….sql` + include w masterze). **Wymaga resetu DB** (drop schematu / tabel + `databasechangelog*`).
- Allegro: konta w osobnej tabeli (zamiast pojedynczych pól w `user_api_credentials`); token OAuth per konto.
- Wybór stawki VAT z Merit: preferencja `Code` równego procentowi (np. `23`), pomijanie stawek zakupowych przy fakturze sprzedaży.
- Formularz „Dodaj fakturę”: wiele pozycji (tabela + dialog szczegółów); kwota netto i VAT faktury sumowane z pozycji.
- MapStruct: mapowania Allegro→faktura, checkout/oferty→DTO, CreateInvoice→Merit oraz token/sold-invoice→entity w pakiecie `mapper`.
- Stawki VAT przy fakturach: GUID i procent z Merit `gettaxes`; przy Allegro dopasowanie do `lineItems[].tax.rate` (gdy puste → 23%).
- Faktura z Allegro: `itemType=1`, kod z `offer.external.id` / `offer.id`, opis i komentarze z danych Allegro (bez fallbacków `ALLEGRO` / „Pozycja Allegro”).
- Formularz faktury: jednostka miary z listy Merit `getunits` (ComboBox); Allegro bierze domyślną jednostkę z Merit.

### Added

- Endpoint listy stawek VAT (`GET /api/taxes`) oraz ComboBox stawek w formularzu „Dodaj fakturę”.
- Shell UI wzorowany na [vaadin-demo](https://github.com/vaadin/vaadin-demo): `AppLayout`, `SideNav`, motyw Aura; logowanie Spring Security (`app_user`, seed `admin`/`admin`).
- Wystawianie faktury Merit z zamówienia Allegro (przycisk w tabeli Sprzedane, zapis numeru w DB).
  Notatka: [docs/plans/allegro-issue-invoice.md](docs/plans/allegro-issue-invoice.md)
- Integracja Allegro Sandbox: OAuth2, lista ofert i sprzedanych pozycji (REST + Vaadin `/allegro`).
  Notatka: [docs/plans/allegro-sandbox-integration.md](docs/plans/allegro-sandbox-integration.md)
- Endpoint listy klientów (`GET /api/customers`, opcjonalny filtr `name`).
  Notatka: [docs/plans/customers-endpoint.md](docs/plans/customers-endpoint.md)
- Formularz „Dodaj fakturę” w Vaadin (dialog z polami API, Zapisz / Anuluj).
  Notatka: [docs/plans/vaadin-create-invoice-form.md](docs/plans/vaadin-create-invoice-form.md)
- Endpoint tworzenia faktury sprzedaży (`POST /api/invoices`) z komentarzem górnym i dolnym.
  Notatka: [docs/plans/create-invoice-endpoint.md](docs/plans/create-invoice-endpoint.md)
- Przycisk wysyłki faktury e-mailem w wierszu listy Vaadin.
  Notatka: [docs/plans/vaadin-invoice-email-button.md](docs/plans/vaadin-invoice-email-button.md)
- PostgreSQL + Liquibase: status wysyłki e-mail faktury (`invoice_email_status`), kolumny „Wysłano” i „Data wysyłki” na liście.
  Notatka: [docs/plans/postgres-invoice-email-status.md](docs/plans/postgres-invoice-email-status.md)

## [0.0.1] - 2026-08-20

### Added

- Ekran listy faktur Vaadin na `/`.
  Notatka: [docs/plans/vaadin-invoice-list.md](docs/plans/vaadin-invoice-list.md)
- Endpoint wysyłki faktury e-mailem (`POST /api/invoices/{id}/email`).
  Notatka: [docs/plans/invoice-email-endpoint.md](docs/plans/invoice-email-endpoint.md)
- Endpoint szczegółów faktury (`GET /api/invoices/{id}`).
  Notatka: [docs/plans/invoice-details-endpoint.md](docs/plans/invoice-details-endpoint.md)
- Swagger UI (springdoc OpenAPI 3.1).
  Notatka: [docs/plans/swagger-openapi.md](docs/plans/swagger-openapi.md)
- Dockerfile i deploy na Render.com.
  Notatka: [docs/plans/dockerfile-render.md](docs/plans/dockerfile-render.md)
- Klient Merit Aktiva — lista faktur sprzedaży.
  Notatka: [docs/plans/merit-invoice-client.md](docs/plans/merit-invoice-client.md)
- Szkielet Spring Boot 4.1 + RestClient.
  Notatka: [docs/plans/spring-boot-skeleton.md](docs/plans/spring-boot-skeleton.md)
- Logowanie request/response Merit (`385e0fd`).

### Changed

- Zakres dat `from`/`to` zamiast endpointu „wczoraj” (`c936f96`).

### Fixed

- Kodowanie podpisu HMAC Merit — Base64 `/` jako `%2F` (`c27455b`).
- Komunikaty błędów Merit zamiast HTTP 500 (`3f099bc`).
- Odpowiedź `"OK"` Merit traktowana jako sukces wysyłki e-mail (`f349218`).
