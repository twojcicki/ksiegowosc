package pl.tw.ksiegowosc.dto;

import java.util.List;

public record AllegroInvoicePreviewDto(
        String orderId,
        Long accountId,
        boolean customerExists,
        String customerId,
        List<AllegroInvoicePreviewRow> rows
) {
}
