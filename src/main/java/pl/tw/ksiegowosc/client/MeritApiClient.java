package pl.tw.ksiegowosc.client;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import pl.tw.ksiegowosc.dto.InvoiceDetailsRequest;
import pl.tw.ksiegowosc.dto.InvoiceListRequest;
import pl.tw.ksiegowosc.dto.MeritCreateInvoiceRequest;
import pl.tw.ksiegowosc.dto.MeritCreateInvoiceResponse;
import pl.tw.ksiegowosc.dto.SalesInvoiceDetailsDto;
import pl.tw.ksiegowosc.dto.SalesInvoiceDto;
import pl.tw.ksiegowosc.dto.SendInvoiceEmailRequest;

@Component
public class MeritApiClient {

    private static final DateTimeFormatter PERIOD_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
    private static final ParameterizedTypeReference<List<SalesInvoiceDto>> INVOICES_RESPONSE =
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
}
