package pl.tw.ksiegowosc.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AllegroSoldItemDto(
        Long accountId,
        String accountName,
        String orderId,
        String name,
        Integer itemCount,
        Integer quantityTotal,
        List<AllegroSoldLineDto> lines,
        BigDecimal totalGross,
        String currency,
        Instant boughtAt,
        String buyerLogin,
        String orderStatus,
        String fulfillmentStatus,
        String invoiceNo,
        String issueError) {
}
