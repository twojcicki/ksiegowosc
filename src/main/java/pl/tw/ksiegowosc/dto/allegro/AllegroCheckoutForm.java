package pl.tw.ksiegowosc.dto.allegro;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AllegroCheckoutForm(
        String id,
        AllegroBuyer buyer,
        String status,
        AllegroFulfillment fulfillment,
        AllegroInvoice invoice,
        List<AllegroLineItem> lineItems) {
}
