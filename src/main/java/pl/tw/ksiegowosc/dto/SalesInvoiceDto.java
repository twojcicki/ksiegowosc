package pl.tw.ksiegowosc.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SalesInvoiceDto(
        @JsonProperty("SIHId") String sihId,
        @JsonProperty("InvoiceNo") String invoiceNo,
        @JsonProperty("DocumentDate") String documentDate,
        @JsonProperty("CustomerName") String customerName,
        @JsonProperty("TaxAmount") BigDecimal taxAmount,
        @JsonProperty("RoundingAmount") BigDecimal roundingAmount,
        @JsonProperty("TotalAmount") BigDecimal totalAmount,
        @JsonProperty("ProfitAmount") BigDecimal profitAmount,
        @JsonProperty("TotalSum") BigDecimal totalSum,
        @JsonProperty("Paid") Boolean paid,
        Boolean emailSent,
        Instant emailSentAt
) {
}
