package pl.tw.ksiegowosc.client;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
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

    public AllegroOffersResponse getOffers(
            String apiBaseUrl,
            String accessToken,
            String userAgent,
            int offset,
            int limit,
            String publicationStatus) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(normalizeBase(apiBaseUrl) + "/sale/offers")
                .queryParam("offset", offset)
                .queryParam("limit", limit);
        if (publicationStatus != null && !publicationStatus.isBlank()) {
            builder.queryParam("publication.status", publicationStatus);
        }

        return allegroRestClient.get()
                .uri(builder.build().toUri())
                .headers(headers -> {
                    headers.setBearerAuth(accessToken);
                    headers.set(HttpHeaders.USER_AGENT, userAgent);
                })
                .retrieve()
                .body(AllegroOffersResponse.class);
    }

    public AllegroCheckoutFormsResponse getCheckoutForms(
            String apiBaseUrl,
            String accessToken,
            String userAgent,
            int offset,
            int limit,
            Instant boughtAtFrom,
            Instant boughtAtTo) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(normalizeBase(apiBaseUrl) + "/order/checkout-forms")
                .queryParam("offset", offset)
                .queryParam("limit", limit);
        if (boughtAtFrom != null) {
            builder.queryParam("lineItems.boughtAt.gte", boughtAtFrom);
        }
        if (boughtAtTo != null) {
            builder.queryParam("lineItems.boughtAt.lte", boughtAtTo);
        }

        return allegroRestClient.get()
                .uri(builder.build().toUri())
                .headers(headers -> {
                    headers.setBearerAuth(accessToken);
                    headers.set(HttpHeaders.USER_AGENT, userAgent);
                })
                .retrieve()
                .body(AllegroCheckoutFormsResponse.class);
    }

    public AllegroCheckoutForm getCheckoutForm(
            String apiBaseUrl, String accessToken, String userAgent, String id) {
        return allegroRestClient.get()
                .uri(normalizeBase(apiBaseUrl) + "/order/checkout-forms/{id}", id)
                .headers(headers -> {
                    headers.setBearerAuth(accessToken);
                    headers.set(HttpHeaders.USER_AGENT, userAgent);
                })
                .retrieve()
                .body(AllegroCheckoutForm.class);
    }

    private static String normalizeBase(String apiBaseUrl) {
        if (apiBaseUrl == null || apiBaseUrl.isBlank()) {
            throw new IllegalArgumentException("apiBaseUrl is required");
        }
        String trimmed = apiBaseUrl.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
