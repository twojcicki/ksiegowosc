package pl.tw.ksiegowosc.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import pl.tw.ksiegowosc.client.AllegroApiClient;
import pl.tw.ksiegowosc.client.AllegroErrorMessages;
import pl.tw.ksiegowosc.dto.BuyerBilling;
import pl.tw.ksiegowosc.dto.CreateInvoiceRequest;
import pl.tw.ksiegowosc.dto.CreateInvoiceResponse;
import pl.tw.ksiegowosc.dto.CustomerDto;
import pl.tw.ksiegowosc.dto.IssueAllegroInvoiceResponse;
import pl.tw.ksiegowosc.dto.MeritCreateCustomerRequest;
import pl.tw.ksiegowosc.dto.MeritCreateCustomerResponse;
import pl.tw.ksiegowosc.dto.allegro.AllegroCheckoutForm;
import pl.tw.ksiegowosc.entity.AllegroSoldInvoice;
import pl.tw.ksiegowosc.mapper.AllegroBillingMapper;
import pl.tw.ksiegowosc.mapper.AllegroInvoiceMapper;
import pl.tw.ksiegowosc.mapper.AllegroInvoiceMappingContext;
import pl.tw.ksiegowosc.mapper.AllegroSoldInvoiceMapper;
import pl.tw.ksiegowosc.repository.AllegroSoldInvoiceRepository;

@Service
public class AllegroInvoiceService {

    private static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");

    private final AllegroApiClient allegroApiClient;
    private final AllegroAuthService authService;
    private final CustomersService customersService;
    private final InvoicesService invoicesService;
    private final TaxesService taxesService;
    private final AllegroSoldInvoiceRepository soldInvoiceRepository;
    private final AllegroBillingMapper billingMapper;
    private final AllegroInvoiceMapper invoiceMapper;
    private final AllegroSoldInvoiceMapper soldInvoiceMapper;
    private final Clock clock;

    public AllegroInvoiceService(
            AllegroApiClient allegroApiClient,
            AllegroAuthService authService,
            CustomersService customersService,
            InvoicesService invoicesService,
            TaxesService taxesService,
            AllegroSoldInvoiceRepository soldInvoiceRepository,
            AllegroBillingMapper billingMapper,
            AllegroInvoiceMapper invoiceMapper,
            AllegroSoldInvoiceMapper soldInvoiceMapper,
            Clock clock) {
        this.allegroApiClient = allegroApiClient;
        this.authService = authService;
        this.customersService = customersService;
        this.invoicesService = invoicesService;
        this.taxesService = taxesService;
        this.soldInvoiceRepository = soldInvoiceRepository;
        this.billingMapper = billingMapper;
        this.invoiceMapper = invoiceMapper;
        this.soldInvoiceMapper = soldInvoiceMapper;
        this.clock = clock;
    }

    @Transactional
    public IssueAllegroInvoiceResponse issueInvoice(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Podaj orderId.");
        }
        String trimmedOrderId = orderId.trim();

        if (soldInvoiceRepository.existsById(trimmedOrderId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Dla tego zamówienia faktura została już wystawiona.");
        }

        authService.getValidAccessToken();
        AllegroCheckoutForm form = fetchCheckoutForm(trimmedOrderId);
        if (form.lineItems() == null || form.lineItems().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Zamówienie nie ma pozycji do zafakturowania.");
        }

        Instant boughtAt = invoiceMapper.earliestBoughtAt(form.lineItems());
        LocalDate docDate = boughtAt == null
                ? LocalDate.now(clock.withZone(ZONE))
                : boughtAt.atZone(ZONE).toLocalDate();
        String invoiceNo = invoiceMapper.buildInvoiceNo(trimmedOrderId, docDate);

        String customerId = resolveCustomerId(form);
        CreateInvoiceRequest request = invoiceMapper.toCreateInvoiceRequest(
                form,
                new AllegroInvoiceMappingContext(customerId, invoiceNo, docDate, taxesService.listTaxes()));
        CreateInvoiceResponse created = invoicesService.createInvoice(request);

        AllegroSoldInvoice entity = soldInvoiceMapper.toEntity(
                trimmedOrderId, invoiceNo, created.invoiceId(), Instant.now(clock));
        soldInvoiceRepository.save(entity);

        return new IssueAllegroInvoiceResponse(invoiceNo, created.invoiceId());
    }

    private AllegroCheckoutForm fetchCheckoutForm(String orderId) {
        try {
            AllegroCheckoutForm form = allegroApiClient.getCheckoutForm(orderId);
            if (form == null || form.id() == null || form.id().isBlank()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleziono zamówienia Allegro.");
            }
            return form;
        } catch (RestClientResponseException ex) {
            org.springframework.http.HttpStatusCode status = ex.getStatusCode().is4xxClientError()
                    ? ex.getStatusCode()
                    : HttpStatus.BAD_GATEWAY;
            throw new ResponseStatusException(status, AllegroErrorMessages.from(ex), ex);
        }
    }

    private String resolveCustomerId(AllegroCheckoutForm form) {
        BuyerBilling billing = billingMapper.toBuyerBilling(form);
        if (billing.vatRegNo() != null && !billing.vatRegNo().isBlank()) {
            List<CustomerDto> existing = customersService.getCustomersByVatRegNo(billing.vatRegNo());
            if (!existing.isEmpty() && existing.getFirst().customerId() != null) {
                return existing.getFirst().customerId();
            }
        }

        MeritCreateCustomerRequest createRequest = invoiceMapper.toCustomerRequest(billing);
        try {
            MeritCreateCustomerResponse created = customersService.createCustomer(createRequest);
            if (created == null || created.id() == null || created.id().isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Merit nie zwrócił identyfikatora utworzonego klienta.");
            }
            return created.id();
        } catch (RestClientResponseException ex) {
            org.springframework.http.HttpStatusCode status = ex.getStatusCode().is4xxClientError()
                    ? ex.getStatusCode()
                    : HttpStatus.BAD_GATEWAY;
            throw new ResponseStatusException(status, "Nie udało się utworzyć klienta w Merit.", ex);
        }
    }
}
