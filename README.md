# Ksiegowosc

Szkielet aplikacji w `Spring Boot 4.1.x` z `Java 25`, `Maven`, `Vaadin 25.2` i klientem HTTP opartym o `RestClient`.

Aplikacja pobiera listę faktur sprzedaży z Merit Aktiva (lokalizacja PL) z podanego zakresu dat. Lista jest dostępna w UI Vaadin oraz przez REST. Integracja z Allegro Sandbox umożliwia podgląd ofert i sprzedanych pozycji.

## Wymagania

- Java 25
- Maven 3.9+
- PostgreSQL 16 (lokalnie przez Docker Compose; produkcja: Render Managed Postgres)
- credentials API Merit: `Api Id` i `Api Key` (Ustawienia >> Ustawienia API)

## Konfiguracja

Adres API i dane uwierzytelniające są w `src/main/resources/application.yml`.
Klucze podawaj przez zmienne środowiskowe, bez wpisywania ich do repozytorium:

```bash
set MERIT_API_ID=twoj-api-id
set MERIT_API_KEY=twoj-api-key
set ALLEGRO_CLIENT_ID=twoj-client-id
set ALLEGRO_CLIENT_SECRET=twoj-client-secret
set ALLEGRO_REDIRECT_URI=http://localhost:8080/api/allegro/auth/callback
```

```yaml
clients:
  merit:
    base-url: https://program.360ksiegowosc.pl/api/v1
    api-id: ${MERIT_API_ID:change-me}
    api-key: ${MERIT_API_KEY:change-me}
    v2-base-url: https://program.360ksiegowosc.pl/api/v2
  allegro:
    api-base-url: https://api.allegro.pl.allegrosandbox.pl
    auth-url: https://allegro.pl.allegrosandbox.pl
    client-id: ${ALLEGRO_CLIENT_ID:change-me}
    client-secret: ${ALLEGRO_CLIENT_SECRET:change-me}
    redirect-uri: ${ALLEGRO_REDIRECT_URI:http://localhost:8080/api/allegro/auth/callback}
    scopes: allegro:api:sale:offers:read allegro:api:orders:read
```

Allegro Sandbox: zarejestruj aplikację na [apps.developer.allegro.pl.allegrosandbox.pl](https://apps.developer.allegro.pl.allegrosandbox.pl/) i ustaw ten sam Redirect URI co w konfiguracji. Połączenie konta: `GET /api/allegro/auth/connect` lub link na stronie `/allegro`.

Klient podpisuje każde żądanie HMAC-SHA256 zgodnie z dokumentacją Merit:
`signature = Base64(HMAC-SHA256(apiId + timestamp + body, apiKey))`.

Baza PostgreSQL (status wysyłki e-mail faktur) — lokalnie domyślnie `jdbc:postgresql://localhost:5432/ksiegowosc` (użytkownik/hasło: `ksiegowosc`). Na Renderze ustaw `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` z Managed Postgres.

## Uruchomienie

Lokalna baza:

```bash
docker compose up -d
```

Aplikacja:

```bash
mvn spring-boot:run
```

Aplikacja wystartuje domyślnie na `http://localhost:8080`. UI używa motywu **Aura** i layoutu jak w [vaadin-demo](https://github.com/vaadin/vaadin-demo) (`AppLayout` + `SideNav`): **Faktury** (`/`) i **Allegro** (`/allegro`).

**Logowanie:** widoki wymagają sesji. Startowy użytkownik (seed przy pierwszym uruchomieniu, jeśli brak w DB): login `admin`, hasło `admin` — zmień hasło w produkcji. Wylogowanie: menu avatara w stopce nawigacji.

W tabeli Sprzedane każdy wiersz to jedno zamówienie; przycisk „Wystaw fakturę” tworzy fakturę w Merit ze wszystkimi pozycjami i zapisuje numer przy zamówieniu. Przycisk „Dodaj fakturę” na liście faktur otwiera formularz tworzenia faktury w Merit. Z wiersza można otworzyć szczegóły (dialog) lub wysłać fakturę e-mailem. Kolumny „Wysłano” i „Data wysyłki” pokazują status z lokalnej bazy. REST i Swagger wymagają zalogowanej sesji.
## Dokumentacja zmian

- [CHANGELOG.md](CHANGELOG.md) — historia zmian z odnośnikami do notatek implementacyjnych
- [docs/plans/](docs/plans/) — notatki planistyczne do poszczególnych funkcji

## Docker

Zbuduj i uruchom obraz lokalnie:

```bash
docker build -t ksiegowosc .
docker run --rm -p 8080:8080 -e MERIT_API_ID=twoj-api-id -e MERIT_API_KEY=twoj-api-key ksiegowosc
```

Aplikacja czyta port ze zmiennej `PORT` (domyślnie `8080`). Render wstrzykuje własne `PORT`.

## Render.com

1. W Renderze utwórz **Web Service** i podłącz repozytorium GitHub.
2. Jako runtime wybierz **Docker** (Render wykryje `Dockerfile` w katalogu głównym).
3. Dodaj sekrety środowiskowe:
   - `MERIT_API_ID`
   - `MERIT_API_KEY`
   - `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` (z Render Managed Postgres)

Obraz budowany jest z profilem Maven `production` (zoptymalizowany frontend Vaadin). Po deployu UI listy faktur będzie pod `/`, Allegro pod `/allegro`, Swagger pod `/swagger-ui.html`, lista REST pod `/api/invoices?from=2026-01-01&to=2026-01-31`, tworzenie faktury pod `POST /api/invoices`, szczegóły pod `/api/invoices/{id}`, wysyłka e-mail pod `POST /api/invoices/{id}/email`, klienci pod `/api/customers`, oferty Allegro pod `/api/allegro/offers`, sprzedane zamówienia pod `/api/allegro/sold-items?from=...&to=...`, wystawienie faktury z Allegro pod `POST /api/allegro/sold-items/invoice`.

## Swagger

Po starcie aplikacji dostępne są:

- UI: `http://localhost:8080/swagger-ui.html`
- specyfikacja OpenAPI: `http://localhost:8080/v3/api-docs`

## Przykładowy endpoint

Pobiera faktury sprzedaży z podanego zakresu według daty dokumentu. Parametry `from` i `to` są w formacie `yyyy-MM-dd`; zakres nie może przekraczać 3 miesięcy (limit API Merit):

```bash
curl "http://localhost:8080/api/invoices?from=2026-01-01&to=2026-01-31"
```

Wywołanie idzie do `POST https://program.360ksiegowosc.pl/api/v1/getinvoices`
z `PeriodStart` i `PeriodEnd` w formacie `yyyyMMdd` oraz `DateType: 0`.

Tworzenie faktury sprzedaży dla istniejącego klienta (GUID). Wymagane m.in. pozycje, VAT, komentarz górny i dolny:

```bash
curl -X POST "http://localhost:8080/api/invoices" ^
  -H "Content-Type: application/json" ^
  -d "{\"customerId\":\"665f01a4-357a-4a6b-a565-2f17e6e1da13\",\"invoiceNo\":\"FV/2026/01/01\",\"docDate\":\"2026-01-01\",\"dueDate\":\"2026-01-15\",\"currencyCode\":\"PLN\",\"headerComment\":\"Komentarz gorny\",\"footerComment\":\"Komentarz dolny\",\"totalAmount\":100.00,\"lines\":[{\"itemCode\":\"USLUGA\",\"description\":\"Usluga\",\"itemType\":2,\"quantity\":1,\"price\":100.00,\"taxId\":\"665f01a4-357a-4a6b-a565-2f17e6e1da13\"}],\"taxAmounts\":[{\"taxId\":\"665f01a4-357a-4a6b-a565-2f17e6e1da13\",\"amount\":23.00}]}"
```

Wywołanie idzie do `POST https://program.360ksiegowosc.pl/api/v1/sendinvoice`
z m.in. `Customer.Id`, `AccountingDoc: 1`, `HComment`, `FComment`.

Lista klientów z Merit. Opcjonalny parametr `name` filtruje po nazwie (dopasowanie częściowe):

```bash
curl "http://localhost:8080/api/customers?name=Firma"
```

Wywołanie idzie do `POST https://program.360ksiegowosc.pl/api/v1/getcustomers`
z body `{}` albo `{ "Name": "..." }`.

Szczegóły pojedynczej faktury (`SIHId` z listy). Opcjonalny parametr `addAttachment=true` dołącza PDF w base64:

```bash
curl "http://localhost:8080/api/invoices/5f91033c-9d0f-416e-a079-d3c892b8c317"
```

Wywołanie idzie do `POST https://program.360ksiegowosc.pl/api/v1/getinvoice`
z `Id` oraz `AddAttachment`.

Wysyłka faktury e-mailem na adres klienta zapisany w Merit. Opcjonalny parametr `delivNote=true` wysyła dokument bez cen:

```bash
curl -X POST "http://localhost:8080/api/invoices/5f91033c-9d0f-416e-a079-d3c892b8c317/email"
```

Wywołanie idzie do `POST https://program.360ksiegowosc.pl/api/v2/sendinvoicebyemail`
z `Id` oraz `DelivNote`.

Wystawienie faktury w Merit dla zamówienia Allegro (wszystkie pozycje, klient find-or-create po NIP):

```bash
curl -X POST "http://localhost:8080/api/allegro/sold-items/invoice" ^
  -H "Content-Type: application/json" ^
  -d "{\"orderId\":\"order-1\"}"
```

## Struktura

- `docs/plans` - notatki implementacyjne (plany zmian)
- `src/main/java/pl/tw/ksiegowosc/entity` - encje JPA
- `src/main/java/pl/tw/ksiegowosc/repository` - repozytoria Spring Data
- `src/main/java/pl/tw/ksiegowosc/ui` - ekrany Vaadin (lista faktur na `/`)
- `src/main/java/pl/tw/ksiegowosc/controller` - endpointy HTTP
- `src/main/java/pl/tw/ksiegowosc/service` - logika aplikacyjna, w tym walidacja zakresu dat
- `src/main/java/pl/tw/ksiegowosc/client` - klient Merit Aktiva
- `src/main/java/pl/tw/ksiegowosc/config` - beany `RestClient` i podpis HMAC
- `src/main/java/pl/tw/ksiegowosc/dto` - DTO żądania i odpowiedzi

## Testy

```bash
mvn test
```
