# Notatki implementacyjne

Indeks planów zmian w repozytorium. Każdy wpis w [CHANGELOG.md](../CHANGELOG.md) linkuje do odpowiedniej notatki.

| Notatka | Commit | Opis |
|---|---|---|
| [spring-boot-skeleton.md](spring-boot-skeleton.md) | `ac62bfe` | Szkielet Spring Boot 4.1 + RestClient |
| [merit-invoice-client.md](merit-invoice-client.md) | `ac62bfe` | Klient Merit Aktiva — lista faktur |
| [swagger-openapi.md](swagger-openapi.md) | `3763723` | Swagger UI (springdoc OpenAPI 3.1) |
| [dockerfile-render.md](dockerfile-render.md) | `958dc19` | Dockerfile i deploy na Render.com |
| [invoice-details-endpoint.md](invoice-details-endpoint.md) | `7bf897d` | GET /api/invoices/{id} |
| [invoice-email-endpoint.md](invoice-email-endpoint.md) | `9467a2b` | POST /api/invoices/{id}/email |
| [create-invoice-endpoint.md](create-invoice-endpoint.md) | — | POST /api/invoices — tworzenie faktury |
| [vaadin-invoice-list.md](vaadin-invoice-list.md) | `9910bda` | Ekran listy faktur Vaadin na `/` |
| [vaadin-create-invoice-form.md](vaadin-create-invoice-form.md) | — | Formularz dodawania faktury w Vaadin |
| [vaadin-invoice-email-button.md](vaadin-invoice-email-button.md) | `6170268` | Przycisk wysyłki e-mail w wierszu listy |
| [postgres-invoice-email-status.md](postgres-invoice-email-status.md) | — | PostgreSQL + status wysyłki e-mail na liście |

Commity bez osobnej notatki (wpisy tylko w CHANGELOG):

- `c936f96` — zakres dat `from`/`to` zamiast endpointu „wczoraj”
- `c27455b` — poprawka kodowania HMAC (Base64 `/` jako `%2F`)
- `3f099bc` — komunikaty błędów Merit zamiast HTTP 500
- `f349218` — odpowiedź `"OK"` Merit jako sukces wysyłki e-mail
- `385e0fd` — logowanie request/response Merit
