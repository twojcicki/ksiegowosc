package pl.tw.ksiegowosc.dto;

import pl.tw.ksiegowosc.mapper.MeritFieldSection;

public record AllegroInvoicePreviewRow(
        MeritFieldSection section,
        String meritField,
        String sourceRule,
        String value
) {
}
