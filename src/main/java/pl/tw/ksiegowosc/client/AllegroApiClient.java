package pl.tw.ksiegowosc.client;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import pl.tw.ksiegowosc.dto.allegro.AllegroCheckoutForm;
import pl.tw.ksiegowosc.dto.allegro.AllegroCheckoutFormsResponse;
import pl.tw.ksiegowosc.dto.allegro.AllegroOffersResponse;

@Component
public class AllegroApiClient {

    private final RestClient allegroRestClient;

    public AllegroApiClient(@Qualifier("allegroRestClient") RestClient allegroRestClient) {
        this.allegroRestClient = allegroRestClient;
    }

    public AllegroOffersResponse getOffers(int offset, int limit, String publicationStatus) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/sale/offers")
                .queryParam("offset", offset)
                .queryParam("limit", limit);
        if (publicationStatus != null && !publicationStatus.isBlank()) {
            builder.queryParam("publication.status", publicationStatus);
        }

        return allegroRestClient.get()
                .uri(builder.build().toUriString())
                .retrieve()
                .body(AllegroOffersResponse.class);
    }

    public AllegroCheckoutFormsResponse getCheckoutForms(
            int offset,
            int limit,
            Instant boughtAtFrom,
            Instant boughtAtTo) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/order/checkout-forms")
                .queryParam("offset", offset)
                .queryParam("limit", limit);
        if (boughtAtFrom != null) {
            builder.queryParam("lineItems.boughtAt.gte", boughtAtFrom);
        }
        if (boughtAtTo != null) {
            builder.queryParam("lineItems.boughtAt.lte", boughtAtTo);
        }

        return allegroRestClient.get()
                .uri(builder.build().toUriString())
                .retrieve()
                .body(AllegroCheckoutFormsResponse.class);
    }

    public AllegroCheckoutForm getCheckoutForm(String id) {
        return allegroRestClient.get()
                .uri("/order/checkout-forms/{id}", id)
                .retrieve()
                .body(AllegroCheckoutForm.class);
    }
}
