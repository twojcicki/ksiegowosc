package pl.tw.ksiegowosc.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateInvoiceRequest(
        @NotBlank String customerId,
        @NotBlank @Size(max = 35) String invoiceNo,
        @NotNull LocalDate docDate,
        @NotNull LocalDate dueDate,
        @NotBlank String currencyCode,
        @NotBlank String headerComment,
        @NotBlank String footerComment,
        @NotNull BigDecimal totalAmount,
        @NotEmpty @Valid List<CreateInvoiceLineRequest> lines,
        @NotEmpty @Valid List<CreateInvoiceTaxAmountRequest> taxAmounts
) {
}
