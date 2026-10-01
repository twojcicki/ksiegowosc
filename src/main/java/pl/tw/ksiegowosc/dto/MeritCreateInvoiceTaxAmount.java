package pl.tw.ksiegowosc.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MeritCreateInvoiceTaxAmount(
        @JsonProperty("TaxId") String taxId,
        @JsonProperty("Amount") BigDecimal amount
) {
}
