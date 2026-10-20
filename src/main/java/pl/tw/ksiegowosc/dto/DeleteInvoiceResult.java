package pl.tw.ksiegowosc.dto;

import java.util.List;

public record DeleteInvoiceResult(
        String invoiceNo,
        List<String> paymentDocumentNos
) {
}
