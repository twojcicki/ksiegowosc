package pl.tw.ksiegowosc.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record AllegroSoldItemDto(
        String orderId,
        String offerId,
        String name,
        Integer quantity,
        BigDecimal price,
        String currency,
        Instant boughtAt,
        String buyerLogin,
        String orderStatus,
        String fulfillmentStatus) {
}
