package pl.tw.ksiegowosc.dto;

import java.time.Instant;

public record AllegroTrialInvoiceDto(
        Long id,
        Long accountId,
        String orderId,
        String payloadJson,
        Instant createdAt,
        String invoiceNo
) {
}
