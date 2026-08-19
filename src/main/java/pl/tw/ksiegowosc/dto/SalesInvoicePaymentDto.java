package pl.tw.ksiegowosc.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SalesInvoicePaymentDto(
        @JsonProperty("PaymDate") String paymDate,
        @JsonProperty("Amount") BigDecimal amount,
        @JsonProperty("PaymentMethod") String paymentMethod,
        @JsonProperty("PaymentId") String paymentId
) {
}
