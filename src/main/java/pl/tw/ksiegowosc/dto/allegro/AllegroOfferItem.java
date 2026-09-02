package pl.tw.ksiegowosc.dto.allegro;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AllegroOfferItem(
        String id,
        String name,
        AllegroSellingMode sellingMode,
        AllegroStock stock,
        AllegroPublication publication) {
}
