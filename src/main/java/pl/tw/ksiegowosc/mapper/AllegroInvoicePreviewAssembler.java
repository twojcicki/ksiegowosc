package pl.tw.ksiegowosc.mapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import pl.tw.ksiegowosc.dto.AllegroInvoicePreviewRow;
import pl.tw.ksiegowosc.dto.MeritCreateCustomerRequest;
import pl.tw.ksiegowosc.dto.MeritCreateInvoiceRequest;
import pl.tw.ksiegowosc.dto.MeritCreateInvoiceRow;
import pl.tw.ksiegowosc.dto.MeritCreateInvoiceTaxAmount;

/**
 * Łączy {@link AllegroMeritInvoiceMappings#RULES} z wartościami zbudowanego payloadu Merit.
 */
public final class AllegroInvoicePreviewAssembler {

    private AllegroInvoicePreviewAssembler() {
    }

    public static List<AllegroInvoicePreviewRow> assemble(
            MeritCreateInvoiceRequest invoice,
            MeritCreateCustomerRequest customerToCreate,
            boolean customerExists) {
        List<AllegroInvoicePreviewRow> rows = new ArrayList<>();

        for (MeritFieldRule rule : AllegroMeritInvoiceMappings.RULES) {
            switch (rule.section()) {
                case CUSTOMER -> appendCustomer(rows, rule, customerToCreate, customerExists);
                case HEADER -> appendHeader(rows, rule, invoice, customerExists);
                case LINE -> appendLines(rows, rule, invoice);
                case TAX -> appendTaxes(rows, rule, invoice);
            }
        }
        return rows;
    }

    private static void appendCustomer(
            List<AllegroInvoicePreviewRow> rows,
            MeritFieldRule rule,
            MeritCreateCustomerRequest customer,
            boolean customerExists) {
        if (customerExists) {
            rows.add(preview(rule, rule.meritField(), "(pominięte — klient już istnieje w Merit)"));
            return;
        }
        if (customer == null) {
            rows.add(preview(rule, rule.meritField(), "(brak — do utworzenia)"));
            return;
        }
        String value = switch (rule.meritField()) {
            case "Name" -> customer.name();
            case "NotTDCustomer" -> String.valueOf(customer.notTdCustomer());
            case "CountryCode" -> customer.countryCode();
            case "VatRegNo" -> customer.vatRegNo();
            case "Address" -> customer.address();
            case "City" -> customer.city();
            case "PostalCode" -> customer.postalCode();
            case "Email" -> customer.email();
            case "CurrencyCode" -> customer.currencyCode();
            case "SalesInvLang" -> customer.salesInvLang();
            default -> null;
        };
        rows.add(preview(rule, rule.meritField(), display(value)));
    }

    private static void appendHeader(
            List<AllegroInvoicePreviewRow> rows,
            MeritFieldRule rule,
            MeritCreateInvoiceRequest invoice,
            boolean customerExists) {
        String value = switch (rule.meritField()) {
            case "Customer.Id" -> {
                String id = invoice.customer() == null ? null : invoice.customer().id();
                if (id == null || id.isBlank()) {
                    yield customerExists ? "" : "(zostanie utworzony przy wystawieniu)";
                }
                yield id;
            }
            case "AccountingDoc" -> String.valueOf(invoice.accountingDoc());
            case "DocDate" -> invoice.docDate();
            case "DueDate" -> invoice.dueDate();
            case "InvoiceNo" -> invoice.invoiceNo();
            case "CurrencyCode" -> invoice.currencyCode();
            case "TotalAmount" -> invoice.totalAmount() == null ? null : invoice.totalAmount().toPlainString();
            case "HComment" -> invoice.hComment();
            case "FComment" -> invoice.fComment();
            default -> null;
        };
        rows.add(preview(rule, rule.meritField(), display(value)));
    }

    private static void appendLines(
            List<AllegroInvoicePreviewRow> rows,
            MeritFieldRule rule,
            MeritCreateInvoiceRequest invoice) {
        List<MeritCreateInvoiceRow> invoiceRows = invoice.invoiceRow() == null ? List.of() : invoice.invoiceRow();
        String template = rule.meritField();
        for (int i = 0; i < invoiceRows.size(); i++) {
            MeritCreateInvoiceRow row = invoiceRows.get(i);
            String field = template.replace("[]", "[" + i + "]");
            String value = switch (template) {
                case "InvoiceRow[].Item.Code" -> row.item() == null ? null : row.item().code();
                case "InvoiceRow[].Item.Description" -> row.item() == null ? null : row.item().description();
                case "InvoiceRow[].Item.Type" -> row.item() == null ? null : String.valueOf(row.item().type());
                case "InvoiceRow[].Item.UOMName" -> row.item() == null ? null : row.item().uomName();
                case "InvoiceRow[].Quantity" -> row.quantity() == null ? null : row.quantity().toPlainString();
                case "InvoiceRow[].Price" -> row.price() == null ? null : row.price().toPlainString();
                case "InvoiceRow[].TaxId" -> row.taxId();
                default -> null;
            };
            rows.add(preview(rule, field, display(value)));
        }
    }

    private static void appendTaxes(
            List<AllegroInvoicePreviewRow> rows,
            MeritFieldRule rule,
            MeritCreateInvoiceRequest invoice) {
        List<MeritCreateInvoiceTaxAmount> taxes = invoice.taxAmount() == null ? List.of() : invoice.taxAmount();
        String template = rule.meritField();
        for (int i = 0; i < taxes.size(); i++) {
            MeritCreateInvoiceTaxAmount tax = taxes.get(i);
            String field = template.replace("[]", "[" + i + "]");
            String value = switch (template) {
                case "TaxAmount[].TaxId" -> tax.taxId();
                case "TaxAmount[].Amount" -> tax.amount() == null ? null : tax.amount().toPlainString();
                default -> null;
            };
            rows.add(preview(rule, field, display(value)));
        }
    }

    private static AllegroInvoicePreviewRow preview(MeritFieldRule rule, String meritField, String value) {
        return new AllegroInvoicePreviewRow(rule.section(), meritField, rule.sourceRule(), value);
    }

    private static String display(String value) {
        return Objects.toString(value, "");
    }
}
