package pl.tw.ksiegowosc.client;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import pl.tw.ksiegowosc.dto.CustomerDto;
import pl.tw.ksiegowosc.dto.InvoiceDetailsRequest;
import pl.tw.ksiegowosc.dto.InvoiceListRequest;
import pl.tw.ksiegowosc.dto.MeritCreateCustomerRequest;
import pl.tw.ksiegowosc.dto.MeritCreateCustomerResponse;
import pl.tw.ksiegowosc.dto.MeritCreateInvoiceRequest;
import pl.tw.ksiegowosc.dto.MeritCreateInvoiceResponse;
import pl.tw.ksiegowosc.dto.MeritCustomersRequest;
import pl.tw.ksiegowosc.dto.MeritTaxDto;
import pl.tw.ksiegowosc.dto.SalesInvoiceDetailsDto;
import pl.tw.ksiegowosc.dto.SalesInvoiceDto;
import pl.tw.ksiegowosc.dto.SendInvoiceEmailRequest;

@Component
public class MeritApiClient {

    private static final DateTimeFormatter PERIOD_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final ParameterizedTypeReference<List<SalesInvoiceDto>> INVOICES_RESPONSE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<List<MeritTaxDto>> TAXES_RESPONSE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient meritRestClient;
    private final RestClient meritV2RestClient;

    public MeritApiClient(
            @Qualifier("meritRestClient") RestClient meritRestClient,
            @Qualifier("meritV2RestClient") RestClient meritV2RestClient) {
        this.meritRestClient = meritRestClient;
        this.meritV2RestClient = meritV2RestClient;
    }

    public List<SalesInvoiceDto> getInvoices(LocalDate from, LocalDate to) {
        InvoiceListRequest request = new InvoiceListRequest(
                from.format(PERIOD_FORMAT),
                to.format(PERIOD_FORMAT),
                0);

        return meritRestClient.post()
                .uri("/getinvoices")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(INVOICES_RESPONSE);
    }

    public SalesInvoiceDetailsDto getInvoiceDetails(String id, boolean addAttachment) {
        InvoiceDetailsRequest request = new InvoiceDetailsRequest(id, addAttachment);

        return meritRestClient.post()
                .uri("/getinvoice")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(SalesInvoiceDetailsDto.class);
    }

    public String sendInvoiceByEmail(String id, boolean delivNote) {
        SendInvoiceEmailRequest request = new SendInvoiceEmailRequest(id, delivNote);

        return meritV2RestClient.post()
                .uri("/sendinvoicebyemail")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(String.class);
    }

    public MeritCreateInvoiceResponse createInvoice(MeritCreateInvoiceRequest request) {
        return meritRestClient.post()
                .uri("/sendinvoice")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(MeritCreateInvoiceResponse.class);
    }

    public List<CustomerDto> getCustomers(String name) {
        return getCustomers(name, null);
    }

    public List<CustomerDto> getCustomers(String name, String vatRegNo) {
        MeritCustomersRequest request = new MeritCustomersRequest(
                blankToNull(name),
                blankToNull(vatRegNo));
        String rawBody = meritRestClient.post()
                .uri("/getcustomers")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(String.class);
        return normalizeCustomers(parseJson(rawBody));
    }

    public MeritCreateCustomerResponse createCustomer(MeritCreateCustomerRequest request) {
        return meritV2RestClient.post()
                .uri("/sendcustomer")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(MeritCreateCustomerResponse.class);
    }

    public List<MeritTaxDto> getTaxes() {
        List<MeritTaxDto> taxes = meritRestClient.post()
                .uri("/gettaxes")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of())
                .retrieve()
                .body(TAXES_RESPONSE);
        return taxes == null ? List.of() : List.copyOf(taxes);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static JsonNode parseJson(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readTree(rawBody);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Nie udało się sparsować odpowiedzi Merit getcustomers.", ex);
        }
    }

    private List<CustomerDto> normalizeCustomers(JsonNode body) {
        if (body == null || body.isNull() || body.isMissingNode()) {
            return List.of();
        }
        if (body.isArray()) {
            if (body.isEmpty()) {
                return List.of();
            }
            List<CustomerDto> customers = new ArrayList<>(body.size());
            for (JsonNode node : body) {
                customers.add(OBJECT_MAPPER.convertValue(node, CustomerDto.class));
            }
            return Collections.unmodifiableList(customers);
        }
        if (body.isObject()) {
            return List.of(OBJECT_MAPPER.convertValue(body, CustomerDto.class));
        }
        return List.of();
    }
}
