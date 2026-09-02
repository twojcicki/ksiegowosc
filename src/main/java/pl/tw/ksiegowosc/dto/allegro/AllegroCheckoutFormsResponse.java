package pl.tw.ksiegowosc.dto.allegro;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AllegroCheckoutFormsResponse(
        List<AllegroCheckoutForm> checkoutForms,
        Integer count,
        Integer totalCount) {
}
