package pl.tw.ksiegowosc.dto;

public record CreateInvoiceResponse(
        String invoiceId,
        String customerId
) {
}
