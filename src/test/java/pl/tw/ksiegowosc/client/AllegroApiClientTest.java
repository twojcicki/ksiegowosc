package pl.tw.ksiegowosc.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import pl.tw.ksiegowosc.config.HttpClientsConfig;
import pl.tw.ksiegowosc.dto.allegro.AllegroCheckoutFormsResponse;
import pl.tw.ksiegowosc.dto.allegro.AllegroOffersResponse;

class AllegroApiClientTest {

    @Test
    void shouldFetchOffers() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.allegro.pl.allegrosandbox.pl")
                .defaultHeader("Accept", HttpClientsConfig.ALLEGRO_ACCEPT)
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().setBearerAuth("test-token");
                    return execution.execute(request, body);
                });
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AllegroApiClient client = new AllegroApiClient(builder.build());

        server.expect(requestTo(startsWith("https://api.allegro.pl.allegrosandbox.pl/sale/offers")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
                .andRespond(withSuccess("""
                        {
                          "offers": [
                            {
                              "id": "1234567890",
                              "name": "Test offer",
                              "sellingMode": {
                                "price": { "amount": "99.99", "currency": "PLN" }
                              },
                              "stock": { "available": 5, "sold": 2 },
                              "publication": { "status": "ACTIVE" }
                            }
                          ],
                          "count": 1,
                          "totalCount": 1
                        }
                        """, MediaType.parseMediaType(HttpClientsConfig.ALLEGRO_ACCEPT)));

        AllegroOffersResponse response = client.getOffers(0, 100, null);

        assertThat(response.offers()).hasSize(1);
        assertThat(response.offers().getFirst().id()).isEqualTo("1234567890");
        assertThat(response.offers().getFirst().name()).isEqualTo("Test offer");
        server.verify();
    }

    @Test
    void shouldFetchCheckoutForms() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.allegro.pl.allegrosandbox.pl")
                .defaultHeader("Accept", HttpClientsConfig.ALLEGRO_ACCEPT)
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().setBearerAuth("test-token");
                    return execution.execute(request, body);
                });
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AllegroApiClient client = new AllegroApiClient(builder.build());

        server.expect(requestTo(startsWith("https://api.allegro.pl.allegrosandbox.pl/order/checkout-forms")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "checkoutForms": [
                            {
                              "id": "order-1",
                              "buyer": { "login": "buyer1" },
                              "status": "READY_FOR_PROCESSING",
                              "fulfillment": { "status": "SENT" },
                              "lineItems": [
                                {
                                  "id": "line-1",
                                  "offerId": "123",
                                  "name": "Sold item",
                                  "quantity": 1,
                                  "price": { "amount": "49.99", "currency": "PLN" },
                                  "boughtAt": "2026-01-15T10:00:00.000Z"
                                }
                              ]
                            }
                          ],
                          "count": 1,
                          "totalCount": 1
                        }
                        """, MediaType.parseMediaType(HttpClientsConfig.ALLEGRO_ACCEPT)));

        AllegroCheckoutFormsResponse response = client.getCheckoutForms(
                0,
                100,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-31T23:59:59Z"));

        assertThat(response.checkoutForms()).hasSize(1);
        assertThat(response.checkoutForms().getFirst().lineItems()).hasSize(1);
        assertThat(response.checkoutForms().getFirst().lineItems().getFirst().name()).isEqualTo("Sold item");
        server.verify();
    }
}
