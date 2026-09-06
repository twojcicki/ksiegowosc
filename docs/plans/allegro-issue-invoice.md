# Wystawianie faktury z zamówienia Allegro

## Zakres

- Tabela „Sprzedane”: **1 wiersz = 1 zamówienie** (`checkout-form`)
- Przycisk **Wystaw fakturę** → faktura Merit ze **wszystkimi pozycjami** zamówienia
- Zapis `invoice_no` w PostgreSQL (`allegro_sold_invoice`) i blokada przycisku

## Numer faktury

`{idZamówieniaBezMyślników}/{MM}/{yyyy}` (data najwcześniejszego `boughtAt`), max 35 znaków (limit Merit).

## Przepływ

1. Guard: jeśli `order_id` już w DB → 409
2. `GET /order/checkout-forms/{id}` (Allegro)
3. Klient Merit: `getcustomers` po `VatRegNo` albo `sendcustomer` (v2)
4. `sendinvoice` z wieloma `InvoiceRow` (ceny Allegro = brutto, VAT 23%)
5. Zapis do `allegro_sold_invoice`

## API

`POST /api/allegro/sold-items/invoice`

```json
{ "orderId": "..." }
```

Odpowiedź `201`: `{ "invoiceNo", "meritInvoiceId" }`

Lista: `GET /api/allegro/sold-items` zwraca zamówienia (nie spłaszczone pozycje) + opcjonalne `invoiceNo`.
