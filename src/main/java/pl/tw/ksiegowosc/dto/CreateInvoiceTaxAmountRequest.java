package pl.tw.ksiegowosc.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;

public record CreateInvoiceTaxAmountRequest(
        @NotBlank String taxId,
        BigDecimal amount
) {
}
