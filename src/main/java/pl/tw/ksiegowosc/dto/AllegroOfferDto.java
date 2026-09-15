package pl.tw.ksiegowosc.dto;

import java.math.BigDecimal;

public record AllegroOfferDto(
        Long accountId,
        String accountName,
        String id,
        String name,
        BigDecimal price,
        String currency,
        Integer available,
        Integer sold,
        String publicationStatus) {
}
