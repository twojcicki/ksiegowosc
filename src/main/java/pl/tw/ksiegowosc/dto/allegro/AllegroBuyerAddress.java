package pl.tw.ksiegowosc.dto.allegro;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AllegroBuyerAddress(
        String street,
        String city,
        String postCode,
        String countryCode) {
}
