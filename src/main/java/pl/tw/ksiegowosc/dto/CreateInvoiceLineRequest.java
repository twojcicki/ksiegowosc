package pl.tw.ksiegowosc.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateInvoiceLineRequest(
        @NotBlank String itemCode,
        @NotBlank String description,
        @NotNull Integer itemType,
        @NotNull BigDecimal quantity,
        @NotNull BigDecimal price,
        @NotBlank String taxId
) {
}
