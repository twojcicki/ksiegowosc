package pl.tw.ksiegowosc.dto.allegro;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AllegroDeliveryAddress(
        String firstName,
        String lastName,
        String street,
        String city,
        String zipCode,
        String countryCode,
        String companyName) {
}
