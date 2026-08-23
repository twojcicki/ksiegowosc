package pl.tw.ksiegowosc.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MeritCreateInvoiceRow(
        @JsonProperty("Item") MeritCreateInvoiceItem item,
        @JsonProperty("Quantity") BigDecimal quantity,
        @JsonProperty("Price") BigDecimal price,
        @JsonProperty("TaxId") String taxId
) {
}
