package pl.tw.ksiegowosc.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MeritCreateInvoiceTaxAmount(
        @JsonProperty("TaxId") String taxId,
        @JsonProperty("Amount") BigDecimal amount
) {
}
