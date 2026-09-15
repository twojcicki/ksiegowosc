package pl.tw.ksiegowosc.dto.allegro;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AllegroAdditionalService(
        String definitionId,
        String name,
        AllegroPrice price,
        Integer quantity) {
}
