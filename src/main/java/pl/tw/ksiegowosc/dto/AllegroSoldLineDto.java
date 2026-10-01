package pl.tw.ksiegowosc.dto;

import java.math.BigDecimal;

public record AllegroSoldLineDto(
        String name,
        Integer quantity,
        BigDecimal unitPriceGross) {
}
