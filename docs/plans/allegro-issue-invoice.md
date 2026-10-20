# Wystawianie faktury z zamówienia Allegro

## Zakres

- Tabela „Sprzedane”: **1 wiersz = 1 zamówienie** (`checkout-form`)
- Przycisk **Wystaw fakturę** → faktura Merit ze **wszystkimi pozycjami towarowymi** oraz **kosztami kupującego** (dostawa, dopłaty, usługi dodatkowe)
- Zakładka **Mapowanie**: katalog reguł + podgląd wartości bez wysyłki
- Zapis `invoice_no` w PostgreSQL (`allegro_sold_invoice`) i blokada przycisku

## Źródło prawdy mapowania

Katalog reguł: `AllegroMeritInvoiceMappings.RULES`  
Budowanie payloadu: `AllegroToMeritInvoiceBuilder`  
Podgląd: `AllegroInvoiceService.previewInvoice` → `AllegroInvoicePreviewAssembler`

Reguły obejmują m.in. `InvoiceRow[].Item.Code|Description|Type|UOMName`, `Quantity`, `Price`, `TaxId` (towary + dostawa/dopłaty).

## Kwota transakcji

Allegro zwraca ceny **brutto**, Merit wymaga **netto** i sam liczy VAT jako `round(suma netto stawki × stawka, 2)`.
Merit zaokrągla każdy wiersz (`Price × Quantity`) do groszy i porównuje sumę wierszy z `TotalAmount`
(błąd: „Suma z artykułów nie zgadza się z wartością faktury”).

Algorytm (`InvoiceNetAllocator` + `AllegroToMeritInvoiceBuilder`), osobno dla każdej stawki VAT:

1. `TotalAmount` grupy = `round(Σ brutto pozycji / (1+VAT), 2)`.
2. Netto pozycji = `brutto pozycji / (1+VAT)` obcięte do groszy; brakujące grosze (0..n) dodawane pozycjom
   o największej reszcie, tak by suma netto pozycji = `TotalAmount` grupy.
3. `Price` = netto pozycji / ilość (7 dp) — po zaokrągleniu `Price × Quantity` do groszy daje dokładnie netto pozycji.
4. `RoundingAmount` = G − Σ po stawkach (`netto + round(netto × VAT)`), gdzie G = `payment.paidAmount`
   (inaczej `summary.totalToPay`). Typowo 0 albo ±0,01; wartość > 0,05 = błąd 400 (brutto Allegro nie zgadza się z pozycjami).

Przykład: towar 403,26 + dostawa 10,95 = 414,21 → wiersze 327,86 + 8,90 = 336,76; Merit VAT 77,45; brutto 414,21; bez korekty.

Składniki:

- `lineItems[].price × quantity`
- `delivery.cost` → pozycja usługa (`Type=2`), `Code=delivery.method.id`, VAT 23%
- `surcharges[].paidAmount` → pozycje usługa, VAT 23%
- `lineItems[].selectedAdditionalServices[].price` → pozycje usługa, VAT 23%

Lista Sprzedane: `totalGross` z `summary.totalToPay` (fallback: suma powyższych).

## Numer faktury

`{prefiksKonta}/{liczbaFakturWMiesiącu+1}/{MM}/{yyyy}` (data najwcześniejszego `boughtAt`), max 35 znaków (limit Merit).

## Przepływ

1. Guard (tylko issue): jeśli `order_id` już w DB → 409
2. `GET /order/checkout-forms/{id}` (Allegro)
3. Klient Merit: `getcustomers` po `VatRegNo` albo `sendcustomer` (v2) — w **preview** bez `sendcustomer`
4. Budowa linii (towary + koszty) i walidacja vs `summary.totalToPay`
5. `sendinvoice` (ceny Allegro = brutto → netto wg algorytmu powyżej; dostawa/dopłaty → 23%)
6. Zapis do `allegro_sold_invoice` (tylko issue)

## API

`POST /api/allegro/sold-items/invoice`

```json
{ "accountId": 1, "orderId": "..." }
```

Odpowiedź `201`: `{ "invoiceNo", "meritInvoiceId" }`

Lista: `GET /api/allegro/sold-items` zwraca zamówienia + `totalGross` (= totalToPay) + opcjonalne `invoiceNo`.

Podgląd mapowania: wywołanie serwisu z UI (bez osobnego REST).
