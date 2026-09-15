package pl.tw.ksiegowosc.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record IssueAllegroInvoiceRequest(
        @NotNull Long accountId,
        @NotBlank String orderId
) {
}
