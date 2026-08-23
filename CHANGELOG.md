# Changelog

Format oparty na [Keep a Changelog](https://keepachangelog.com/pl/1.1.0/).

## [Unreleased]

### Added

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
