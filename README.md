# Ksiegowosc

Szkielet aplikacji w `Spring Boot 4.1.x` z `Java 25`, `Maven` i klientem HTTP opartym o `RestClient`.

Aplikacja pobiera listę faktur sprzedaży z Merit Aktiva (lokalizacja PL) z podanego zakresu dat.

## Wymagania

- Java 25
- Maven 3.9+
- credentials API Merit: `Api Id` i `Api Key` (Ustawienia >> Ustawienia API)

## Konfiguracja

Adres API i dane uwierzytelniające są w `src/main/resources/application.yml`.
Klucze podawaj przez zmienne środowiskowe, bez wpisywania ich do repozytorium:

```bash
set MERIT_API_ID=twoj-api-id
set MERIT_API_KEY=twoj-api-key
```

```yaml
clients:
  merit:
    base-url: https://program.360ksiegowosc.pl/api/v1
    api-id: ${MERIT_API_ID:change-me}
    api-key: ${MERIT_API_KEY:change-me}
```

Klient podpisuje każde żądanie HMAC-SHA256 zgodnie z dokumentacją Merit:
`signature = Base64(HMAC-SHA256(apiId + timestamp + body, apiKey))`.

## Uruchomienie

```bash
mvn spring-boot:run
```

Aplikacja wystartuje domyślnie na `http://localhost:8080`.

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

Po deployu Swagger będzie pod `/swagger-ui.html`, a lista faktur pod `/api/invoices?from=2026-01-01&to=2026-01-31`.

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

## Struktura

- `src/main/java/pl/tw/ksiegowosc/controller` - endpointy HTTP
- `src/main/java/pl/tw/ksiegowosc/service` - logika aplikacyjna, w tym walidacja zakresu dat
- `src/main/java/pl/tw/ksiegowosc/client` - klient Merit Aktiva
- `src/main/java/pl/tw/ksiegowosc/config` - beany `RestClient` i podpis HMAC
- `src/main/java/pl/tw/ksiegowosc/dto` - DTO żądania i odpowiedzi

## Testy

```bash
mvn test
```
