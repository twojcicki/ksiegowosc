package pl.tw.ksiegowosc.dto.allegro;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AllegroPrice(
        String amount,
        String currency) {
}
