package pl.tw.ksiegowosc.dto;

import java.util.List;

public record BulkDeleteInvoiceResult(
        List<DeleteInvoiceResult> successes,
        List<BulkDeleteInvoiceFailure> failures
) {
}
