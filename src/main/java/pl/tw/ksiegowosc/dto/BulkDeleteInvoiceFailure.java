package pl.tw.ksiegowosc.dto;

public record BulkDeleteInvoiceFailure(
        String id,
        String invoiceNo,
        String message
) {
}
