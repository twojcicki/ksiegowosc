package pl.tw.ksiegowosc.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
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
import pl.tw.ksiegowosc.dto.MeritCredentials;
import pl.tw.ksiegowosc.dto.CustomerDto;
import pl.tw.ksiegowosc.dto.MeritCreateInvoiceCustomer;
import pl.tw.ksiegowosc.dto.MeritCreateInvoiceItem;
import pl.tw.ksiegowosc.dto.MeritCreateInvoiceRequest;
import pl.tw.ksiegowosc.dto.MeritCreateInvoiceResponse;
import pl.tw.ksiegowosc.dto.MeritCreateInvoiceRow;
import pl.tw.ksiegowosc.dto.MeritCreateInvoiceTaxAmount;
import pl.tw.ksiegowosc.dto.SalesInvoiceDetailsDto;
import pl.tw.ksiegowosc.dto.SalesInvoiceDto;

class MeritApiClientTest {

    @Test
    void shouldFetchInvoicesForGivenPeriod() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-18T10:00:00Z"), ZoneOffset.UTC);
        MeritApiProperties properties = new MeritApiProperties(
                "https://program.360ksiegowosc.pl/api/v1",
                "https://program.360ksiegowosc.pl/api/v2");
        MeritAuthInterceptor interceptor = new MeritAuthInterceptor(
                () -> new MeritCredentials("test-api-id", "test-api-key"), clock);

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestInterceptor(interceptor);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MeritApiClient client = new MeritApiClient(builder.build(), RestClient.builder().build());

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

    @Test
    void shouldFetchInvoiceDetails() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-18T10:00:00Z"), ZoneOffset.UTC);
        MeritApiProperties properties = new MeritApiProperties(
                "https://program.360ksiegowosc.pl/api/v1",
                "https://program.360ksiegowosc.pl/api/v2");
        MeritAuthInterceptor interceptor = new MeritAuthInterceptor(
                () -> new MeritCredentials("test-api-id", "test-api-key"), clock);

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestInterceptor(interceptor);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MeritApiClient client = new MeritApiClient(builder.build(), RestClient.builder().build());

        String invoiceId = "5f91033c-9d0f-416e-a079-d3c892b8c317";
        String expectedBody = "{\"Id\":\"" + invoiceId + "\",\"AddAttachment\":false}";
        String expectedSignature = interceptor.sign("20260818100000", expectedBody);

        server.expect(requestTo(startsWith("https://program.360ksiegowosc.pl/api/v1/getinvoice")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(queryParam("apiId", "test-api-id"))
                .andExpect(queryParam("timestamp", "20260818100000"))
                .andExpect(queryParam("signature", URLEncoder.encode(expectedSignature, StandardCharsets.UTF_8)))
                .andExpect(content().json(expectedBody, true))
                .andRespond(withSuccess("""
                        {
                          "Header": {
                            "SIHId": "5f91033c-9d0f-416e-a079-d3c892b8c317",
                            "InvoiceNo": "FV/2026/08/17",
                            "CustomerName": "Przykładowy Klient",
                            "TotalAmount": 123.45
                          },
                          "Lines": [
                            {
                              "ArticleCode": "ABC",
                              "Quantity": 1,
                              "Price": 123.45,
                              "Description": "Usługa"
                            }
                          ],
                          "Payments": []
                        }
                        """, MediaType.APPLICATION_JSON));

        SalesInvoiceDetailsDto details = client.getInvoiceDetails(invoiceId, false);

        assertThat(details.header().invoiceNo()).isEqualTo("FV/2026/08/17");
        assertThat(details.lines()).hasSize(1);
        assertThat(details.lines().getFirst().articleCode()).isEqualTo("ABC");
        server.verify();
    }

    @Test
    void shouldSendInvoiceByEmail() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-18T10:00:00Z"), ZoneOffset.UTC);
        MeritApiProperties properties = new MeritApiProperties(
                "https://program.360ksiegowosc.pl/api/v1",
                "https://program.360ksiegowosc.pl/api/v2");
        MeritAuthInterceptor interceptor = new MeritAuthInterceptor(
                () -> new MeritCredentials("test-api-id", "test-api-key"), clock);

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.v2BaseUrl())
                .requestInterceptor(interceptor);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MeritApiClient client = new MeritApiClient(RestClient.builder().build(), builder.build());

        String invoiceId = "5f91033c-9d0f-416e-a079-d3c892b8c317";
        String expectedBody = "{\"Id\":\"" + invoiceId + "\",\"DelivNote\":false}";
        String expectedSignature = interceptor.sign("20260818100000", expectedBody);

        server.expect(requestTo(startsWith("https://program.360ksiegowosc.pl/api/v2/sendinvoicebyemail")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(queryParam("apiId", "test-api-id"))
                .andExpect(queryParam("timestamp", "20260818100000"))
                .andExpect(queryParam("signature", URLEncoder.encode(expectedSignature, StandardCharsets.UTF_8)))
                .andExpect(content().json(expectedBody, true))
                .andRespond(withSuccess("OK", MediaType.TEXT_PLAIN));

        String result = client.sendInvoiceByEmail(invoiceId, false);

        assertThat(result).isEqualTo("OK");
        server.verify();
    }

    @Test
    void shouldCreateInvoice() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-18T10:00:00Z"), ZoneOffset.UTC);
        MeritApiProperties properties = new MeritApiProperties(
                "https://program.360ksiegowosc.pl/api/v1",
                "https://program.360ksiegowosc.pl/api/v2");
        MeritAuthInterceptor interceptor = new MeritAuthInterceptor(
                () -> new MeritCredentials("test-api-id", "test-api-key"), clock);

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestInterceptor(interceptor);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MeritApiClient client = new MeritApiClient(builder.build(), RestClient.builder().build());

        MeritCreateInvoiceRequest request = new MeritCreateInvoiceRequest(
                new MeritCreateInvoiceCustomer("665f01a4-357a-4a6b-a565-2f17e6e1da13"),
                1,
                "20260101000000",
                "20260115000000",
                "FV/2026/01/01",
                "PLN",
                List.of(new MeritCreateInvoiceRow(
                        new MeritCreateInvoiceItem("USLUGA", "Usluga", 2, null),
                        new BigDecimal("1.00"),
                        new BigDecimal("100.00"),
                        "665f01a4-357a-4a6b-a565-2f17e6e1da13")),
                List.of(new MeritCreateInvoiceTaxAmount(
                        "665f01a4-357a-4a6b-a565-2f17e6e1da13",
                        new BigDecimal("23.00"))),
                new BigDecimal("100.00"),
                "Komentarz gorny",
                "Komentarz dolny");

        server.expect(requestTo(startsWith("https://program.360ksiegowosc.pl/api/v1/sendinvoice")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(queryParam("apiId", "test-api-id"))
                .andExpect(queryParam("timestamp", "20260818100000"))
                .andExpect(content().json("""
                        {
                          "Customer": { "Id": "665f01a4-357a-4a6b-a565-2f17e6e1da13" },
                          "AccountingDoc": 1,
                          "DocDate": "20260101000000",
                          "DueDate": "20260115000000",
                          "InvoiceNo": "FV/2026/01/01",
                          "CurrencyCode": "PLN",
                          "InvoiceRow": [{
                            "Item": { "Code": "USLUGA", "Description": "Usluga", "Type": 2 },
                            "Quantity": 1.00,
                            "Price": 100.00,
                            "TaxId": "665f01a4-357a-4a6b-a565-2f17e6e1da13"
                          }],
                          "TaxAmount": [{
                            "TaxId": "665f01a4-357a-4a6b-a565-2f17e6e1da13",
                            "Amount": 23.00
                          }],
                          "TotalAmount": 100.00,
                          "HComment": "Komentarz gorny",
                          "FComment": "Komentarz dolny"
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "CustomerId": "665f01a4-357a-4a6b-a565-2f17e6e1da13",
                          "InvoiceId": "5f91033c-9d0f-416e-a079-d3c892b8c317"
                        }
                        """, MediaType.APPLICATION_JSON));

        MeritCreateInvoiceResponse response = client.createInvoice(request);

        assertThat(response.invoiceId()).isEqualTo("5f91033c-9d0f-416e-a079-d3c892b8c317");
        assertThat(response.customerId()).isEqualTo("665f01a4-357a-4a6b-a565-2f17e6e1da13");
        server.verify();
    }

    @Test
    void shouldFetchCustomersAsList() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-18T10:00:00Z"), ZoneOffset.UTC);
        MeritApiProperties properties = new MeritApiProperties(
                "https://program.360ksiegowosc.pl/api/v1",
                "https://program.360ksiegowosc.pl/api/v2");
        MeritAuthInterceptor interceptor = new MeritAuthInterceptor(
                () -> new MeritCredentials("test-api-id", "test-api-key"), clock);

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestInterceptor(interceptor);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MeritApiClient client = new MeritApiClient(builder.build(), RestClient.builder().build());

        server.expect(requestTo(startsWith("https://program.360ksiegowosc.pl/api/v1/getcustomers")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"Name\":\"Firma\"}"))
                .andRespond(withSuccess("""
                        [
                          {
                            "CustomerId": "11111111-1111-1111-1111-111111111111",
                            "Name": "Firma A",
                            "RegNo": "111",
                            "Email": "a@example.com"
                          },
                          {
                            "CustomerId": "22222222-2222-2222-2222-222222222222",
                            "Name": "Firma B",
                            "RegNo": "222"
                          }
                        ]
                        """, MediaType.APPLICATION_JSON));

        List<CustomerDto> customers = client.getCustomers("Firma");

        assertThat(customers).hasSize(2);
        assertThat(customers.getFirst().name()).isEqualTo("Firma A");
        assertThat(customers.get(1).customerId()).isEqualTo("22222222-2222-2222-2222-222222222222");
        server.verify();
    }

    @Test
    void shouldWrapSingleCustomerObjectAsList() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-18T10:00:00Z"), ZoneOffset.UTC);
        MeritApiProperties properties = new MeritApiProperties(
                "https://program.360ksiegowosc.pl/api/v1",
                "https://program.360ksiegowosc.pl/api/v2");
        MeritAuthInterceptor interceptor = new MeritAuthInterceptor(
                () -> new MeritCredentials("test-api-id", "test-api-key"), clock);

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestInterceptor(interceptor);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MeritApiClient client = new MeritApiClient(builder.build(), RestClient.builder().build());

        server.expect(requestTo(startsWith("https://program.360ksiegowosc.pl/api/v1/getcustomers")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{}"))
                .andRespond(withSuccess("""
                        {
                          "CustomerId": "11111111-1111-1111-1111-111111111111",
                          "Name": "Jedyny Klient",
                          "City": "Warszawa"
                        }
                        """, MediaType.APPLICATION_JSON));

        List<CustomerDto> customers = client.getCustomers(null);

        assertThat(customers).hasSize(1);
        assertThat(customers.getFirst().name()).isEqualTo("Jedyny Klient");
        assertThat(customers.getFirst().city()).isEqualTo("Warszawa");
        server.verify();
    }

    @Test
    void shouldFetchCustomersByVatRegNo() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-18T10:00:00Z"), ZoneOffset.UTC);
        MeritApiProperties properties = new MeritApiProperties(
                "https://program.360ksiegowosc.pl/api/v1",
                "https://program.360ksiegowosc.pl/api/v2");
        MeritAuthInterceptor interceptor = new MeritAuthInterceptor(
                () -> new MeritCredentials("test-api-id", "test-api-key"), clock);

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestInterceptor(interceptor);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MeritApiClient client = new MeritApiClient(builder.build(), RestClient.builder().build());

        server.expect(requestTo(startsWith("https://program.360ksiegowosc.pl/api/v1/getcustomers")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"VatRegNo\":\"5252674798\"}"))
                .andRespond(withSuccess("""
                        {
                          "CustomerId": "11111111-1111-1111-1111-111111111111",
                          "Name": "Allegro",
                          "VatRegNo": "5252674798"
                        }
                        """, MediaType.APPLICATION_JSON));

        List<CustomerDto> customers = client.getCustomers(null, "5252674798");

        assertThat(customers).hasSize(1);
        assertThat(customers.getFirst().vatRegNo()).isEqualTo("5252674798");
        server.verify();
    }

    @Test
    void shouldCreateCustomer() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-18T10:00:00Z"), ZoneOffset.UTC);
        MeritApiProperties properties = new MeritApiProperties(
                "https://program.360ksiegowosc.pl/api/v1",
                "https://program.360ksiegowosc.pl/api/v2");
        MeritAuthInterceptor interceptor = new MeritAuthInterceptor(
                () -> new MeritCredentials("test-api-id", "test-api-key"), clock);

        RestClient.Builder v2Builder = RestClient.builder()
                .baseUrl(properties.v2BaseUrl())
                .requestInterceptor(interceptor);
        MockRestServiceServer server = MockRestServiceServer.bindTo(v2Builder).build();
        MeritApiClient client = new MeritApiClient(RestClient.builder().build(), v2Builder.build());

        pl.tw.ksiegowosc.dto.MeritCreateCustomerRequest request =
                new pl.tw.ksiegowosc.dto.MeritCreateCustomerRequest(
                        "Klient",
                        true,
                        "PL",
                        null,
                        "Ul. Testowa 1",
                        "Warszawa",
                        "00-001",
                        "a@example.com",
                        "PLN",
                        "PL");

        server.expect(requestTo(startsWith("https://program.360ksiegowosc.pl/api/v2/sendcustomer")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {
                          "Name": "Klient",
                          "NotTDCustomer": true,
                          "CountryCode": "PL",
                          "Address": "Ul. Testowa 1",
                          "City": "Warszawa",
                          "PostalCode": "00-001",
                          "Email": "a@example.com",
                          "CurrencyCode": "PLN",
                          "SalesInvLang": "PL"
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "Id": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                          "Name": "Klient"
                        }
                        """, MediaType.APPLICATION_JSON));

        pl.tw.ksiegowosc.dto.MeritCreateCustomerResponse response = client.createCustomer(request);

        assertThat(response.id()).isEqualTo("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        assertThat(response.name()).isEqualTo("Klient");
        server.verify();
    }

    @Test
    void shouldFetchTaxes() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-18T10:00:00Z"), ZoneOffset.UTC);
        MeritApiProperties properties = new MeritApiProperties(
                "https://program.360ksiegowosc.pl/api/v1",
                "https://program.360ksiegowosc.pl/api/v2");
        MeritAuthInterceptor interceptor = new MeritAuthInterceptor(
                () -> new MeritCredentials("test-api-id", "test-api-key"), clock);

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestInterceptor(interceptor);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MeritApiClient client = new MeritApiClient(builder.build(), RestClient.builder().build());

        String expectedBody = "{}";
        String expectedSignature = interceptor.sign("20260818100000", expectedBody);

        server.expect(requestTo(startsWith("https://program.360ksiegowosc.pl/api/v1/gettaxes")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(queryParam("apiId", "test-api-id"))
                .andExpect(queryParam("timestamp", "20260818100000"))
                .andExpect(queryParam("signature", URLEncoder.encode(expectedSignature, StandardCharsets.UTF_8)))
                .andExpect(content().json(expectedBody, true))
                .andRespond(withSuccess("""
                        [
                          {
                            "Id": "973a4395-665f-47a6-a5b6-5384dd24f8d0",
                            "Code": "23",
                            "Name": "VAT 23%",
                            "TaxPct": 23.00
                          },
                          {
                            "Id": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
                            "Code": "8",
                            "Name": "VAT 8%",
                            "TaxPct": 8.00
                          }
                        ]
                        """, MediaType.APPLICATION_JSON));

        List<pl.tw.ksiegowosc.dto.MeritTaxDto> taxes = client.getTaxes();

        assertThat(taxes).hasSize(2);
        assertThat(taxes.getFirst().id()).isEqualTo("973a4395-665f-47a6-a5b6-5384dd24f8d0");
        assertThat(taxes.getFirst().taxPct()).isEqualByComparingTo("23.00");
        assertThat(taxes.get(1).code()).isEqualTo("8");
        server.verify();
    }

    @Test
    void shouldFetchUnits() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-18T10:00:00Z"), ZoneOffset.UTC);
        MeritApiProperties properties = new MeritApiProperties(
                "https://program.360ksiegowosc.pl/api/v1",
                "https://program.360ksiegowosc.pl/api/v2");
        MeritAuthInterceptor interceptor = new MeritAuthInterceptor(
                () -> new MeritCredentials("test-api-id", "test-api-key"), clock);

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestInterceptor(interceptor);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MeritApiClient client = new MeritApiClient(builder.build(), RestClient.builder().build());

        String expectedBody = "{}";
        String expectedSignature = interceptor.sign("20260818100000", expectedBody);

        server.expect(requestTo(startsWith("https://program.360ksiegowosc.pl/api/v1/getunits")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(queryParam("apiId", "test-api-id"))
                .andExpect(queryParam("timestamp", "20260818100000"))
                .andExpect(queryParam("signature", URLEncoder.encode(expectedSignature, StandardCharsets.UTF_8)))
                .andExpect(content().json(expectedBody, true))
                .andRespond(withSuccess("""
                        [
                          { "Code": "KG", "Name": "kg" },
                          { "Code": "SZT", "Name": "szt." }
                        ]
                        """, MediaType.APPLICATION_JSON));

        List<pl.tw.ksiegowosc.dto.MeritUnitDto> units = client.getUnits();

        assertThat(units).hasSize(2);
        assertThat(units.get(1).name()).isEqualTo("szt.");
        server.verify();
    }
}
