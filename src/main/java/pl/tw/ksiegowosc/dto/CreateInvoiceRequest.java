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
        LocalDate dueDate,
        LocalDate transactionDate,
        @NotBlank String currencyCode,
        @NotBlank String headerComment,
        String footerComment,
        @NotNull BigDecimal totalAmount,
        BigDecimal roundingAmount,
        @NotEmpty @Valid List<CreateInvoiceLineRequest> lines,
        @NotEmpty @Valid List<CreateInvoiceTaxAmountRequest> taxAmounts,
        @Valid CreateInvoicePaymentRequest payment
) {
}
