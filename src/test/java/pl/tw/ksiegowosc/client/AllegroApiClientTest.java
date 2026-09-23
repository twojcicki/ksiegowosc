package pl.tw.ksiegowosc.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
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

    private static final String API_BASE = "https://api.allegro.pl.allegrosandbox.pl";
    private static final String USER_AGENT = "Ksiegowosc-Test/0.0.1 (+https://example.test)";

    private MockRestServiceServer server;
    private AllegroApiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .defaultHeader("Accept", HttpClientsConfig.ALLEGRO_ACCEPT);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new AllegroApiClient(builder.build());
    }

    @Test
    void shouldFetchOffers() {
        server.expect(requestTo(startsWith(API_BASE + "/sale/offers")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
                .andExpect(header(HttpHeaders.USER_AGENT, USER_AGENT))
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

        AllegroOffersResponse response = client.getOffers(API_BASE, "test-token", USER_AGENT, 0, 100, null);

        assertThat(response.offers()).hasSize(1);
        assertThat(response.offers().getFirst().id()).isEqualTo("1234567890");
        assertThat(response.offers().getFirst().name()).isEqualTo("Test offer");
        server.verify();
    }

    @Test
    void shouldFetchCheckoutForms() {
        server.expect(requestTo(startsWith(API_BASE + "/order/checkout-forms")))
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
                                  "offer": { "id": "123", "name": "Sold item" },
                                  "quantity": 1,
                                  "price": { "amount": "49.99", "currency": "PLN" },
                                  "tax": { "rate": "23.00", "subject": "GOODS" },
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
                API_BASE,
                "test-token",
                USER_AGENT,
                0,
                100,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-31T23:59:59Z"));

        assertThat(response.checkoutForms()).hasSize(1);
        assertThat(response.checkoutForms().getFirst().lineItems()).hasSize(1);
        assertThat(response.checkoutForms().getFirst().lineItems().getFirst().offer().name()).isEqualTo("Sold item");
        server.verify();
    }

    @Test
    void shouldFetchCheckoutFormById() {
        server.expect(requestTo(API_BASE + "/order/checkout-forms/order-1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "id": "order-1",
                          "buyer": { "login": "buyer1", "email": "a@example.com" },
                          "status": "READY_FOR_PROCESSING",
                          "invoice": {
                            "required": true,
                            "address": {
                              "street": "Testowa 1",
                              "city": "Poznań",
                              "zipCode": "60-001",
                              "countryCode": "PL",
                              "company": {
                                "name": "Firma",
                                "taxId": "5252674798"
                              }
                            }
                          },
                          "lineItems": [
                            {
                              "id": "line-1",
                              "offer": {
                                "id": "123",
                                "name": "Sold item",
                                "external": { "id": "SKU-001" }
                              },
                              "quantity": 1,
                              "price": { "amount": "49.99", "currency": "PLN" },
                              "tax": { "rate": "23.00", "subject": "GOODS", "exemption": null },
                              "boughtAt": "2026-01-15T10:00:00.000Z"
                            }
                          ],
                          "delivery": {
                            "cost": { "amount": "8.99", "currency": "PLN" },
                            "method": { "id": "method-1", "name": "Paczkomat" }
                          },
                          "summary": {
                            "totalToPay": { "amount": "58.98", "currency": "PLN" }
                          }
                        }
                        """, MediaType.parseMediaType(HttpClientsConfig.ALLEGRO_ACCEPT)));

        var form = client.getCheckoutForm(API_BASE, "test-token", USER_AGENT, "order-1");

        assertThat(form.id()).isEqualTo("order-1");
        assertThat(form.buyer().email()).isEqualTo("a@example.com");
        assertThat(form.invoice().address().company().taxId()).isEqualTo("5252674798");
        assertThat(form.lineItems()).hasSize(1);
        assertThat(form.lineItems().getFirst().offer().id()).isEqualTo("123");
        assertThat(form.lineItems().getFirst().offer().external().id()).isEqualTo("SKU-001");
        assertThat(form.lineItems().getFirst().tax().rate()).isEqualTo("23.00");
        assertThat(form.delivery().cost().amount()).isEqualTo("8.99");
        assertThat(form.delivery().method().id()).isEqualTo("method-1");
        assertThat(form.summary().totalToPay().amount()).isEqualTo("58.98");
        server.verify();
    }
}
