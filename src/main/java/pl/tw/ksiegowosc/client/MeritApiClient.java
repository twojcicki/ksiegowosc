package pl.tw.ksiegowosc.client;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import pl.tw.ksiegowosc.dto.InvoiceListRequest;
import pl.tw.ksiegowosc.dto.SalesInvoiceDto;

@Component
public class MeritApiClient {

    private static final DateTimeFormatter PERIOD_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
    private static final ParameterizedTypeReference<List<SalesInvoiceDto>> INVOICES_RESPONSE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient meritRestClient;

    public MeritApiClient(RestClient meritRestClient) {
        this.meritRestClient = meritRestClient;
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
}
