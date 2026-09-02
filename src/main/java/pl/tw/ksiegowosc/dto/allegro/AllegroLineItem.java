package pl.tw.ksiegowosc.dto.allegro;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AllegroLineItem(
        String id,
        String offerId,
        String name,
        Integer quantity,
        AllegroPrice price,
        Instant boughtAt) {
}
