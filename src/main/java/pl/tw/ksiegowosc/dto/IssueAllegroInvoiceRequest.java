package pl.tw.ksiegowosc.dto;

import jakarta.validation.constraints.NotBlank;

public record IssueAllegroInvoiceRequest(
        @NotBlank String orderId
) {
}
