package pl.tw.ksiegowosc.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record AllegroSoldItemDto(
        String orderId,
        String name,
        Integer itemCount,
        BigDecimal totalGross,
        String currency,
        Instant boughtAt,
        String buyerLogin,
        String orderStatus,
        String fulfillmentStatus,
        String invoiceNo) {
}
