# Ksiegowosc

Szkielet aplikacji w `Spring Boot 4.1.x` z `Java 25`, `Maven` i klientem HTTP opartym o `RestClient`.

Aplikacja pobiera listę faktur sprzedaży z Merit Aktiva (lokalizacja PL) za wczorajszy dzień.

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

## Przykładowy endpoint

Pobiera faktury sprzedaży z wczorajszego dnia (data dokumentu, strefa `Europe/Warsaw`):

```bash
curl http://localhost:8080/api/invoices/yesterday
```

Wywołanie idzie do `POST https://program.360ksiegowosc.pl/api/v1/getinvoices`
z `PeriodStart` i `PeriodEnd` ustawionymi na wczoraj (`yyyyMMdd`) oraz `DateType: 0`.

## Struktura

- `src/main/java/pl/tw/ksiegowosc/controller` - endpointy HTTP
- `src/main/java/pl/tw/ksiegowosc/service` - logika aplikacyjna, w tym wyliczenie wczorajszej daty
- `src/main/java/pl/tw/ksiegowosc/client` - klient Merit Aktiva
- `src/main/java/pl/tw/ksiegowosc/config` - beany `RestClient` i podpis HMAC
- `src/main/java/pl/tw/ksiegowosc/dto` - DTO żądania i odpowiedzi

## Testy

```bash
mvn test
```
