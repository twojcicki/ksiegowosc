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
import pl.tw.ksiegowosc.dto.AllegroInvoicePreviewDto;
import pl.tw.ksiegowosc.dto.AllegroInvoicePreviewRow;
import pl.tw.ksiegowosc.dto.BuyerBilling;
import pl.tw.ksiegowosc.dto.CreateInvoiceRequest;
import pl.tw.ksiegowosc.dto.CreateInvoiceResponse;
import pl.tw.ksiegowosc.dto.CustomerDto;
import pl.tw.ksiegowosc.dto.IssueAllegroInvoiceResponse;
import pl.tw.ksiegowosc.dto.MeritCreateCustomerRequest;
import pl.tw.ksiegowosc.dto.MeritCreateCustomerResponse;
import pl.tw.ksiegowosc.dto.MeritCreateInvoiceRequest;
import pl.tw.ksiegowosc.dto.allegro.AllegroCheckoutForm;
import pl.tw.ksiegowosc.entity.AllegroAccount;
import pl.tw.ksiegowosc.entity.AllegroSoldInvoice;
import pl.tw.ksiegowosc.mapper.AllegroBillingMapper;
import pl.tw.ksiegowosc.mapper.AllegroInvoiceMapper;
import pl.tw.ksiegowosc.mapper.AllegroInvoiceMappingContext;
import pl.tw.ksiegowosc.mapper.AllegroInvoicePreviewAssembler;
import pl.tw.ksiegowosc.mapper.AllegroSoldInvoiceMapper;
import pl.tw.ksiegowosc.mapper.MeritInvoiceMapper;
import pl.tw.ksiegowosc.repository.AllegroSoldInvoiceRepository;

@Service
public class AllegroInvoiceService {

    private static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");

    private final AllegroApiClient allegroApiClient;
    private final AllegroAuthService authService;
    private final AllegroAccountService accountService;
    private final CustomersService customersService;
    private final InvoicesService invoicesService;
    private final TaxesService taxesService;
    private final UnitsService unitsService;
    private final AllegroSoldInvoiceRepository soldInvoiceRepository;
    private final AllegroBillingMapper billingMapper;
    private final AllegroInvoiceMapper invoiceMapper;
    private final MeritInvoiceMapper meritInvoiceMapper;
    private final AllegroSoldInvoiceMapper soldInvoiceMapper;
    private final Clock clock;

    public AllegroInvoiceService(
            AllegroApiClient allegroApiClient,
            AllegroAuthService authService,
            AllegroAccountService accountService,
            CustomersService customersService,
            InvoicesService invoicesService,
            TaxesService taxesService,
            UnitsService unitsService,
            AllegroSoldInvoiceRepository soldInvoiceRepository,
            AllegroBillingMapper billingMapper,
            AllegroInvoiceMapper invoiceMapper,
            MeritInvoiceMapper meritInvoiceMapper,
            AllegroSoldInvoiceMapper soldInvoiceMapper,
            Clock clock) {
        this.allegroApiClient = allegroApiClient;
        this.authService = authService;
        this.accountService = accountService;
        this.customersService = customersService;
        this.invoicesService = invoicesService;
        this.taxesService = taxesService;
        this.unitsService = unitsService;
        this.soldInvoiceRepository = soldInvoiceRepository;
        this.billingMapper = billingMapper;
        this.invoiceMapper = invoiceMapper;
        this.meritInvoiceMapper = meritInvoiceMapper;
        this.soldInvoiceMapper = soldInvoiceMapper;
        this.clock = clock;
    }

    @Transactional
    public IssueAllegroInvoiceResponse issueInvoice(Long accountId, String orderId) {
        PreparedAllegroInvoice prepared = prepareInvoice(accountId, orderId, true);
        CreateInvoiceResponse created = invoicesService.createInvoice(prepared.request());

        AllegroSoldInvoice entity = soldInvoiceMapper.toEntity(
                prepared.orderId(), prepared.request().invoiceNo(), created.invoiceId(), Instant.now(clock));
        soldInvoiceRepository.save(entity);

        return new IssueAllegroInvoiceResponse(prepared.request().invoiceNo(), created.invoiceId());
    }

    public AllegroInvoicePreviewDto previewInvoice(Long accountId, String orderId) {
        PreparedAllegroInvoice prepared = prepareInvoice(accountId, orderId, false);
        MeritCreateInvoiceRequest meritRequest = meritInvoiceMapper.toMeritRequest(prepared.request());
        List<AllegroInvoicePreviewRow> rows = AllegroInvoicePreviewAssembler.assemble(
                meritRequest,
                prepared.customerToCreate(),
                prepared.customerExists());
        return new AllegroInvoicePreviewDto(
                prepared.orderId(),
                accountId,
                prepared.customerExists(),
                prepared.request().customerId(),
                rows);
    }

    private PreparedAllegroInvoice prepareInvoice(Long accountId, String orderId, boolean createMissingCustomer) {
        if (accountId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Podaj accountId.");
        }
        if (orderId == null || orderId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Podaj orderId.");
        }
        String trimmedOrderId = orderId.trim();

        if (createMissingCustomer && soldInvoiceRepository.existsById(trimmedOrderId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Dla tego zamówienia faktura została już wystawiona.");
        }

        AllegroAccount account = accountService.requireOwnedAccount(accountId);
        String accessToken = authService.getValidAccessTokenForAccount(account);
        AllegroCheckoutForm form = fetchCheckoutForm(
                account.getApiBaseUrl(), accessToken, account.getUserAgent(), trimmedOrderId);
        if (form.lineItems() == null || form.lineItems().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Zamówienie nie ma pozycji do zafakturowania.");
        }

        Instant boughtAt = invoiceMapper.earliestBoughtAt(form.lineItems());
        LocalDate docDate = boughtAt == null
                ? LocalDate.now(clock.withZone(ZONE))
                : boughtAt.atZone(ZONE).toLocalDate();
        String invoiceNo;
        try {
            invoiceNo = accountService.allocateInvoiceNo(accountId, docDate);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nie udało się zbudować numeru faktury.", ex);
        }

        CustomerResolution customer = resolveCustomer(form, createMissingCustomer);
        String uomName = unitsService.requireDefaultUnit().name();
        if (uomName == null || uomName.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Wybrana jednostka miary z Merit nie ma nazwy.");
        }
        CreateInvoiceRequest request = invoiceMapper.toCreateInvoiceRequest(
                form,
                new AllegroInvoiceMappingContext(
                        customer.customerId(), invoiceNo, docDate, taxesService.listTaxes(), uomName.trim()));
        return new PreparedAllegroInvoice(
                trimmedOrderId,
                request,
                customer.exists(),
                customer.toCreate());
    }

    private AllegroCheckoutForm fetchCheckoutForm(
            String apiBaseUrl, String accessToken, String userAgent, String orderId) {
        try {
            AllegroCheckoutForm form = allegroApiClient.getCheckoutForm(apiBaseUrl, accessToken, userAgent, orderId);
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

    private CustomerResolution resolveCustomer(AllegroCheckoutForm form, boolean createMissing) {
        BuyerBilling billing = billingMapper.toBuyerBilling(form);
        if (billing.vatRegNo() != null && !billing.vatRegNo().isBlank()) {
            List<CustomerDto> existing = customersService.getCustomersByVatRegNo(billing.vatRegNo());
            if (!existing.isEmpty() && existing.getFirst().customerId() != null) {
                return new CustomerResolution(existing.getFirst().customerId(), true, null);
            }
        }

        MeritCreateCustomerRequest createRequest = invoiceMapper.toCustomerRequest(billing);
        if (!createMissing) {
            return new CustomerResolution(null, false, createRequest);
        }

        try {
            MeritCreateCustomerResponse created = customersService.createCustomer(createRequest);
            if (created == null || created.id() == null || created.id().isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Merit nie zwrócił identyfikatora utworzonego klienta.");
            }
            return new CustomerResolution(created.id(), false, createRequest);
        } catch (RestClientResponseException ex) {
            org.springframework.http.HttpStatusCode status = ex.getStatusCode().is4xxClientError()
                    ? ex.getStatusCode()
                    : HttpStatus.BAD_GATEWAY;
            throw new ResponseStatusException(status, "Nie udało się utworzyć klienta w Merit.", ex);
        }
    }

    private record CustomerResolution(
            String customerId,
            boolean exists,
            MeritCreateCustomerRequest toCreate
    ) {
    }

    private record PreparedAllegroInvoice(
            String orderId,
            CreateInvoiceRequest request,
            boolean customerExists,
            MeritCreateCustomerRequest customerToCreate
    ) {
    }
}
