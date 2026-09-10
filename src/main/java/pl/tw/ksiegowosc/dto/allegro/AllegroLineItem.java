package pl.tw.ksiegowosc.dto.allegro;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AllegroLineItem(
        String id,
        AllegroOfferReference offer,
        Integer quantity,
        AllegroPrice price,
        AllegroLineItemTax tax,
        Instant boughtAt) {
}
