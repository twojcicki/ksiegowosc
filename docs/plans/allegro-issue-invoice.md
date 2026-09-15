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

Brutto faktury (suma netto + VAT) musi = `summary.totalToPay.amount` (±0,01 PLN).

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
5. `sendinvoice` (ceny Allegro = brutto → netto wg VAT; dostawa/dopłaty → 23%)
6. Zapis do `allegro_sold_invoice` (tylko issue)

## API

`POST /api/allegro/sold-items/invoice`

```json
{ "accountId": 1, "orderId": "..." }
```

Odpowiedź `201`: `{ "invoiceNo", "meritInvoiceId" }`

Lista: `GET /api/allegro/sold-items` zwraca zamówienia + `totalGross` (= totalToPay) + opcjonalne `invoiceNo`.

Podgląd mapowania: wywołanie serwisu z UI (bez osobnego REST).
