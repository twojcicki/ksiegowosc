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
                    "invoice.address.company.name; inaczej naturalPerson (imię+nazwisko); inaczej buyer.login; inaczej „Klient Allegro”"),
            rule(MeritFieldSection.CUSTOMER, "NotTDCustomer",
                    "false gdy jest nazwa firmy i NIP; w przeciwnym razie true"),
            rule(MeritFieldSection.CUSTOMER, "CountryCode",
                    "invoice.address.countryCode; domyślnie PL"),
            rule(MeritFieldSection.CUSTOMER, "VatRegNo",
                    "company.ids (PL_NIP/OTHER lub pierwszy); inaczej company.taxId"),
            rule(MeritFieldSection.CUSTOMER, "Address",
                    "invoice.address.street"),
            rule(MeritFieldSection.CUSTOMER, "City",
                    "invoice.address.city"),
            rule(MeritFieldSection.CUSTOMER, "PostalCode",
                    "invoice.address.zipCode"),
            rule(MeritFieldSection.CUSTOMER, "Email",
                    "buyer.email"),
            rule(MeritFieldSection.CUSTOMER, "CurrencyCode",
                    "stała PLN"),
            rule(MeritFieldSection.CUSTOMER, "SalesInvLang",
                    "stała PL"),

            // HEADER (sendinvoice)
            rule(MeritFieldSection.HEADER, "Customer.Id",
                    "istniejący klient Merit po VatRegNo; inaczej id z sendcustomer"),
            rule(MeritFieldSection.HEADER, "AccountingDoc",
                    "stała 1 (faktura sprzedaży)"),
            rule(MeritFieldSection.HEADER, "DocDate",
                    "najwcześniejszy lineItems[].boughtAt (Europe/Warsaw); inaczej dziś"),
            rule(MeritFieldSection.HEADER, "DueDate",
                    "DocDate + 14 dni"),
            rule(MeritFieldSection.HEADER, "InvoiceNo",
                    "prefiks konta / (liczba faktur w miesiącu + 1) / MM / rrrr"),
            rule(MeritFieldSection.HEADER, "CurrencyCode",
                    "ostatnie niepuste lineItems[].price.currency; domyślnie PLN"),
            rule(MeritFieldSection.HEADER, "TotalAmount",
                    "suma netto pozycji (2 miejsca, HALF_UP)"),
            rule(MeritFieldSection.HEADER, "HComment",
                    "orderId / buyer.login (z fallbackiem gdy brak jednej strony)"),
            rule(MeritFieldSection.HEADER, "FComment",
                    "NIP gdy jest; inaczej orderId"),

            // LINE (InvoiceRow + Item)
            rule(MeritFieldSection.LINE, "InvoiceRow[].Item.Code",
                    "offer.external.id; inaczej offer.id (max 20 znaków)"),
            rule(MeritFieldSection.LINE, "InvoiceRow[].Item.Description",
                    "offer.name"),
            rule(MeritFieldSection.LINE, "InvoiceRow[].Item.Type",
                    "stała 1 (towar/stock)"),
            rule(MeritFieldSection.LINE, "InvoiceRow[].Item.UOMName",
                    "domyślna jednostka z Merit getunits"),
            rule(MeritFieldSection.LINE, "InvoiceRow[].Quantity",
                    "lineItems[].quantity; domyślnie 1"),
            rule(MeritFieldSection.LINE, "InvoiceRow[].Price",
                    "cena netto jednostkowa z Allegro gross / (1+VAT)"),
            rule(MeritFieldSection.LINE, "InvoiceRow[].TaxId",
                    "Merit TaxId z gettaxes po lineItems[].tax.rate; brak rate → 23%"),

            // TAX
            rule(MeritFieldSection.TAX, "TaxAmount[].TaxId",
                    "TaxId z pozycji po zgrupowaniu"),
            rule(MeritFieldSection.TAX, "TaxAmount[].Amount",
                    "suma VAT pozycji o tym samym TaxId (brutto − netto)")
    );

    private AllegroMeritInvoiceMappings() {
    }

    private static MeritFieldRule rule(MeritFieldSection section, String meritField, String sourceRule) {
        return new MeritFieldRule(section, meritField, sourceRule);
    }
}
