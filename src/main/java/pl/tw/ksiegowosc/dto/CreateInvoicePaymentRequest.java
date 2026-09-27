package pl.tw.ksiegowosc.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateInvoicePaymentRequest(
        @NotBlank String paymentMethod,
        @NotNull BigDecimal paidAmount,
        @NotBlank String paymDate
) {
}
