package pl.tw.ksiegowosc.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import pl.tw.ksiegowosc.config.MeritApiProperties;
import pl.tw.ksiegowosc.config.MeritAuthInterceptor;
import pl.tw.ksiegowosc.dto.SalesInvoiceDto;

class MeritApiClientTest {

    @Test
    void shouldFetchInvoicesForGivenPeriod() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-18T10:00:00Z"), ZoneOffset.UTC);
        MeritApiProperties properties = new MeritApiProperties(
                "https://program.360ksiegowosc.pl/api/v1",
                "test-api-id",
                "test-api-key");
        MeritAuthInterceptor interceptor = new MeritAuthInterceptor(properties, clock);

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestInterceptor(interceptor);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MeritApiClient client = new MeritApiClient(builder.build());

        String expectedBody = "{\"PeriodStart\":\"20260801\",\"PeriodEnd\":\"20260817\",\"DateType\":0}";
        String expectedSignature = interceptor.sign("20260818100000", expectedBody);

        server.expect(requestTo(startsWith("https://program.360ksiegowosc.pl/api/v1/getinvoices")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(queryParam("apiId", "test-api-id"))
                .andExpect(queryParam("timestamp", "20260818100000"))
                .andExpect(queryParam("signature", URLEncoder.encode(expectedSignature, StandardCharsets.UTF_8)))
                .andExpect(content().json(expectedBody, true))
                .andRespond(withSuccess("""
                        [
                          {
                            "SIHId": "5f91033c-9d0f-416e-a079-d3c892b8c317",
                            "InvoiceNo": "FV/2026/08/17",
                            "DocumentDate": "2026-08-17T00:00:00",
                            "CustomerName": "Przykładowy Klient",
                            "TotalAmount": 123.45,
                            "Paid": false
                          }
                        ]
                        """, MediaType.APPLICATION_JSON));

        List<SalesInvoiceDto> invoices = client.getInvoices(
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 17));

        assertThat(invoices).hasSize(1);
        assertThat(invoices.getFirst().invoiceNo()).isEqualTo("FV/2026/08/17");
        assertThat(invoices.getFirst().customerName()).isEqualTo("Przykładowy Klient");
        server.verify();
    }
}
