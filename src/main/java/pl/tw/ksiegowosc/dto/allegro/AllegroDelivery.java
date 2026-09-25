package pl.tw.ksiegowosc.dto.allegro;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AllegroDelivery(
        AllegroPrice cost,
        AllegroDeliveryMethod method,
        AllegroDeliveryAddress address) {
}
