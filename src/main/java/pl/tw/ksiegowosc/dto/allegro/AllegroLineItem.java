package pl.tw.ksiegowosc.dto.allegro;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AllegroLineItem(
        String id,
        AllegroOfferReference offer,
        Integer quantity,
        AllegroPrice price,
        AllegroLineItemTax tax,
        Instant boughtAt,
        List<AllegroAdditionalService> selectedAdditionalServices) {
}
