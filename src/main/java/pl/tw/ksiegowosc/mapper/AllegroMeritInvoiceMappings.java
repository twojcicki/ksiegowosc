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
                    "invoice.address.company.name; inaczej naturalPerson (imię+nazwisko); inaczej „Klient Allegro (login|email)”"),
            rule(MeritFieldSection.CUSTOMER, "NotTDCustomer",
                    "false gdy jest nazwa firmy i NIP; w przeciwnym razie true"),
            rule(MeritFieldSection.CUSTOMER, "CountryCode",
                    "cały invoice.address.countryCode; inaczej cały delivery.address; domyślnie PL"),
            rule(MeritFieldSection.CUSTOMER, "VatRegNo",
                    "company.ids (PL_NIP/OTHER lub pierwszy); inaczej company.taxId"),
            rule(MeritFieldSection.CUSTOMER, "Address",
                    "cały invoice.address.street; inaczej cały delivery.address.street"),
            rule(MeritFieldSection.CUSTOMER, "City",
                    "cały invoice.address.city; inaczej cały delivery.address.city"),
            rule(MeritFieldSection.CUSTOMER, "PostalCode",
                    "cały invoice.address.zipCode; inaczej cały delivery.address.zipCode"),
            rule(MeritFieldSection.CUSTOMER, "Email",
                    "buyer.email"),
            rule(MeritFieldSection.CUSTOMER, "CurrencyCode",
                    "stała PLN"),
            rule(MeritFieldSection.CUSTOMER, "SalesInvLang",
                    "stała PL"),

            // HEADER (sendinvoice)
            rule(MeritFieldSection.HEADER, "Customer.Id",
                    "istniejący klient Merit po VatRegNo; bez NIP po exact Name (filtr „Klient Allegro” dla nazw syntetycznych); inaczej sendcustomer"),
            rule(MeritFieldSection.HEADER, "AccountingDoc",
                    "stała 1 (faktura sprzedaży)"),
            rule(MeritFieldSection.HEADER, "DocDate",
                    "najwcześniejszy lineItems[].boughtAt (Europe/Warsaw); inaczej dziś"),
            rule(MeritFieldSection.HEADER, "DueDate",
                    "DocDate + 14 dni"),
            rule(MeritFieldSection.HEADER, "InvoiceNo",
                    "prefiks konta / (liczba faktur w miesiącu + 1) / MM / rrrr"),
            rule(MeritFieldSection.HEADER, "CurrencyCode",
                    "lineItems/delivery/summary currency; domyślnie PLN"),
            rule(MeritFieldSection.HEADER, "TotalAmount",
                    "suma netto pozycji (towary + dostawa + dopłaty); brutto faktury = summary.totalToPay (±0,01)"),
            rule(MeritFieldSection.HEADER, "HComment",
                    "AllegroAccount.name, login z GET /me, „ID transakcji: ” + orderId / buyer.login"),
            rule(MeritFieldSection.HEADER, "FComment",
                    "nie ustawiane"),

            // LINE (InvoiceRow + Item) — towary, dostawa, dopłaty, usługi dodatkowe
            rule(MeritFieldSection.LINE, "InvoiceRow[].Item.Code",
                    "towar: offer.external.id/offer.id; dostawa: delivery.method.id (max 20); dopłata: surcharge.id; usługa: definitionId"),
            rule(MeritFieldSection.LINE, "InvoiceRow[].Item.Description",
                    "towar: offer.name; dostawa: method.name/„Dostawa”; dopłata: „Dopłata …”; usługa: name"),
            rule(MeritFieldSection.LINE, "InvoiceRow[].Item.Type",
                    "towar: 1 (stock); dostawa/dopłaty/usługi: 2 (service)"),
            rule(MeritFieldSection.LINE, "InvoiceRow[].Item.UOMName",
                    "domyślna jednostka z Merit getunits"),
            rule(MeritFieldSection.LINE, "InvoiceRow[].Quantity",
                    "towar/usługa: quantity; dostawa/dopłata: 1"),
            rule(MeritFieldSection.LINE, "InvoiceRow[].Price",
                    "netto z brutto Allegro / (1+VAT); brutto z lineItems.price, delivery.cost, surcharges.paidAmount, additionalServices.price"),
            rule(MeritFieldSection.LINE, "InvoiceRow[].TaxId",
                    "towar: lineItems[].tax.rate; dostawa/dopłaty/usługi: fallback 23%"),

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
