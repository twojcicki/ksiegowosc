package pl.tw.ksiegowosc.mapper;

import java.time.LocalDate;
import java.util.List;

import pl.tw.ksiegowosc.dto.MeritTaxDto;

public record AllegroInvoiceMappingContext(
        String customerId,
        String invoiceNo,
        LocalDate docDate,
        List<MeritTaxDto> taxes) {
}
