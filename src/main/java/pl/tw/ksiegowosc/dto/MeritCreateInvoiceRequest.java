package pl.tw.ksiegowosc.dto;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MeritCreateInvoiceRequest(
        @JsonProperty("Customer") MeritCreateInvoiceCustomer customer,
        @JsonProperty("AccountingDoc") int accountingDoc,
        @JsonProperty("DocDate") String docDate,
        @JsonProperty("DueDate") String dueDate,
        @JsonProperty("InvoiceNo") String invoiceNo,
        @JsonProperty("CurrencyCode") String currencyCode,
        @JsonProperty("InvoiceRow") List<MeritCreateInvoiceRow> invoiceRow,
        @JsonProperty("TaxAmount") List<MeritCreateInvoiceTaxAmount> taxAmount,
        @JsonProperty("TotalAmount") BigDecimal totalAmount,
        @JsonProperty("HComment") String hComment,
        @JsonProperty("FComment") String fComment
) {
}
