package pl.tw.ksiegowosc.mapper;

import java.util.List;

/**
 * Deklaratywny katalog mapowania Allegro → Merit (sendinvoice / sendcustomer).
 * Wspólne źródło dla buildera, tabeli reguł w GUI i podglądu wartości.
 */
public final class AllegroMeritInvoiceMappings {

    public static final List<MeritFieldRule> RULES = List.of(
            // CUSTOMER (sendcustomer)
            rule(MeritFieldSection.CUSTOMER, "Name",
                    "gdy invoice.required: company.name → naturalPerson; inaczej delivery.address.firstName+lastName"),
            rule(MeritFieldSection.CUSTOMER, "NotTDCustomer",
                    "false gdy invoice.required i nazwa firmy z NIP; w przeciwnym razie true"),
            rule(MeritFieldSection.CUSTOMER, "CountryCode",
                    "gdy invoice.required: invoice.address; inaczej delivery.address; domyślnie PL"),
            rule(MeritFieldSection.CUSTOMER, "VatRegNo",
                    "tylko gdy invoice.required: company.ids (PL_NIP/OTHER lub pierwszy); inaczej company.taxId"),
            rule(MeritFieldSection.CUSTOMER, "Address",
                    "gdy invoice.required: invoice.address.street; inaczej delivery.address.street"),
            rule(MeritFieldSection.CUSTOMER, "City",
                    "gdy invoice.required: invoice.address.city; inaczej delivery.address.city"),
            rule(MeritFieldSection.CUSTOMER, "PostalCode",
                    "gdy invoice.required: invoice.address.zipCode; inaczej delivery.address.zipCode"),
            rule(MeritFieldSection.CUSTOMER, "Email",
                    "buyer.email"),
            rule(MeritFieldSection.CUSTOMER, "CurrencyCode",
                    "stała PLN"),
            rule(MeritFieldSection.CUSTOMER, "SalesInvLang",
                    "stała PL"),

            // HEADER (sendinvoice)
            rule(MeritFieldSection.HEADER, "Customer.Id",
                    "sendcustomer; przy api-custexists — CustomerId z getcustomers po Name"),
            rule(MeritFieldSection.HEADER, "AccountingDoc",
                    "stała 1 (faktura sprzedaży)"),
            rule(MeritFieldSection.HEADER, "DocDate",
                    "to samo co TransactionDate"),
            rule(MeritFieldSection.HEADER, "TransactionDate",
                    "najwcześniejszy lineItems[].boughtAt (Europe/Warsaw); inaczej dziś"),
            rule(MeritFieldSection.HEADER, "DueDate",
                    "nie ustawiane"),
            rule(MeritFieldSection.HEADER, "InvoiceNo",
                    "prefiks konta / (liczba faktur w miesiącu + 1) / MM / rrrr"),
            rule(MeritFieldSection.HEADER, "CurrencyCode",
                    "lineItems/delivery/summary currency; domyślnie PLN"),
            rule(MeritFieldSection.HEADER, "TotalAmount",
                    "round(Σ (Price×Quantity), 2); Price z brutto/szt. Allegro; brutto faktury ≈ TotalAmount + TaxAmount = paidAmount/totalToPay"),
            rule(MeritFieldSection.HEADER, "HComment",
                    "AllegroAccount.name, login z GET /me, „ID transakcji: ” + orderId / buyer.login"),
            rule(MeritFieldSection.HEADER, "FComment",
                    "nie ustawiane"),
            rule(MeritFieldSection.HEADER, "Payment.PaymentMethod",
                    "allegro_account.payment_method (konfiguracja konta)"),
            rule(MeritFieldSection.HEADER, "Payment.PaidAmount",
                    "payment.paidAmount.amount gdy opłacone; inaczej pominięte"),
            rule(MeritFieldSection.HEADER, "Payment.PaymDate",
                    "zawsze payment.finishedAt (yyyyMMddHHmmss Europe/Warsaw); bez finishedAt — bez Payment"),

            // LINE (InvoiceRow + Item) — towary, dostawa, dopłaty, usługi dodatkowe
            rule(MeritFieldSection.LINE, "InvoiceRow[].Item.Code",
                    "towar: offer.external.id/offer.id; dostawa: stałe „Dostawa”; dopłata: surcharge.id; usługa: definitionId"),
            rule(MeritFieldSection.LINE, "InvoiceRow[].Item.Description",
                    "towar: offer.name; dostawa: method.name/„Dostawa”; dopłata: „Dopłata …”; usługa: name"),
            rule(MeritFieldSection.LINE, "InvoiceRow[].Item.Type",
                    "towar: 1 (stock); dostawa/dopłaty/usługi: 2 (service)"),
            rule(MeritFieldSection.LINE, "InvoiceRow[].Item.UOMName",
                    "domyślna jednostka z Merit getunits"),
            rule(MeritFieldSection.LINE, "InvoiceRow[].Quantity",
                    "towar/usługa: quantity; dostawa/dopłata: 1"),
            rule(MeritFieldSection.LINE, "InvoiceRow[].Price",
                    "netto/szt. = brutto/szt. Allegro / (1+VAT), 7 dp; lineNet = Price×qty (7 dp); TotalAmount = round(Σ lineNet, 2)"),
            rule(MeritFieldSection.LINE, "InvoiceRow[].TaxId",
                    "towar: lineItems[].tax.rate; dostawa/dopłaty/usługi: fallback 23%"),

            // TAX
            rule(MeritFieldSection.TAX, "TaxAmount[].TaxId",
                    "TaxId z pozycji po zgrupowaniu"),
            rule(MeritFieldSection.TAX, "TaxAmount[].Amount",
                    "VAT jako reszta (brutto linii − round(lineNet, 2)); dopięcie sumy do TotalAmount + Tax = Σ brutto")
    );

    private AllegroMeritInvoiceMappings() {
    }

    private static MeritFieldRule rule(MeritFieldSection section, String meritField, String sourceRule) {
        return new MeritFieldRule(section, meritField, sourceRule);
    }
}
