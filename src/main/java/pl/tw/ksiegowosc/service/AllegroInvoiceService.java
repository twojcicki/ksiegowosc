package pl.tw.ksiegowosc.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import pl.tw.ksiegowosc.client.AllegroApiClient;
import pl.tw.ksiegowosc.client.AllegroErrorMessages;
import pl.tw.ksiegowosc.client.MeritErrorMessages;
import pl.tw.ksiegowosc.dto.AllegroInvoicePreviewDto;
import pl.tw.ksiegowosc.dto.AllegroInvoicePreviewRow;
import pl.tw.ksiegowosc.dto.BuyerBilling;
import pl.tw.ksiegowosc.dto.CreateInvoiceRequest;
import pl.tw.ksiegowosc.dto.CreateInvoiceResponse;
import pl.tw.ksiegowosc.dto.IssueAllegroInvoiceResponse;
import pl.tw.ksiegowosc.dto.MeritCreateCustomerRequest;
import pl.tw.ksiegowosc.dto.MeritCreateCustomerResponse;
import pl.tw.ksiegowosc.dto.MeritCreateInvoiceRequest;
import pl.tw.ksiegowosc.dto.allegro.AllegroCheckoutForm;
import pl.tw.ksiegowosc.dto.allegro.AllegroMe;
import pl.tw.ksiegowosc.entity.AllegroAccount;
import pl.tw.ksiegowosc.entity.AllegroSoldInvoice;
import pl.tw.ksiegowosc.entity.AllegroTrialInvoice;
import pl.tw.ksiegowosc.mapper.AllegroBillingMapper;
import pl.tw.ksiegowosc.mapper.AllegroInvoiceMapper;
import pl.tw.ksiegowosc.mapper.AllegroInvoiceMappingContext;
import pl.tw.ksiegowosc.mapper.AllegroInvoicePreviewAssembler;
import pl.tw.ksiegowosc.mapper.AllegroSoldInvoiceMapper;
import pl.tw.ksiegowosc.mapper.MeritInvoiceMapper;
import pl.tw.ksiegowosc.repository.AllegroSoldInvoiceRepository;

@Service
public class AllegroInvoiceService {

    private static final Logger log = LoggerFactory.getLogger(AllegroInvoiceService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");

    private final AllegroApiClient allegroApiClient;
    private final AllegroAuthService authService;
    private final AllegroAccountService accountService;
    private final CustomersService customersService;
    private final InvoicesService invoicesService;
    private final TaxesService taxesService;
    private final UnitsService unitsService;
    private final AllegroSoldInvoiceRepository soldInvoiceRepository;
    private final AllegroTrialInvoiceService trialInvoiceService;
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
            AllegroTrialInvoiceService trialInvoiceService,
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
        this.trialInvoiceService = trialInvoiceService;
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
        entity.setIssueError(null);
        entity.setIssueErrorAt(null);
        soldInvoiceRepository.save(entity);

        return new IssueAllegroInvoiceResponse(prepared.request().invoiceNo(), created.invoiceId());
    }

    /**
     * Builds Merit payload without sendinvoice / create customer and stores it as a trial invoice.
     */
    @Transactional
    public AllegroTrialInvoice issueTrialInvoice(Long userId, Long accountId, String orderId) {
        PreparedAllegroInvoice prepared = prepareInvoice(accountId, orderId, false);
        MeritCreateInvoiceRequest meritRequest = meritInvoiceMapper.toMeritRequest(prepared.request());
        String payloadJson;
        try {
            payloadJson = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(meritRequest);
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Nie udało się zserializować payloadu faktury próbnej.",
                    ex);
        }
        return trialInvoiceService.save(userId, accountId, prepared.orderId(), payloadJson);
    }

    @Transactional
    public void recordIssueError(String orderId, String errorMessage) {
        if (orderId == null || orderId.isBlank()) {
            return;
        }
        String trimmed = orderId.trim();
        AllegroSoldInvoice entity = soldInvoiceRepository.findById(trimmed).orElseGet(AllegroSoldInvoice::new);
        if (entity.getOrderId() == null) {
            entity.setOrderId(trimmed);
            entity.setCreatedAt(Instant.now(clock));
        }
        entity.setIssueError(errorMessage == null || errorMessage.isBlank()
                ? "Nie udało się wystawić faktury."
                : errorMessage.trim());
        entity.setIssueErrorAt(Instant.now(clock));
        soldInvoiceRepository.save(entity);
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

        AllegroAccount account = accountService.requireOwnedAccount(accountId);
        String accessToken = authService.getValidAccessTokenForAccount(account);
        String sellerLogin = fetchSellerLogin(account.getApiBaseUrl(), accessToken, account.getUserAgent());
        AllegroCheckoutForm form = fetchCheckoutForm(
                account.getApiBaseUrl(), accessToken, account.getUserAgent(), trimmedOrderId);
        if (!createMissingCustomer) {
            log.info(
                    "Allegro invoice preview orderId={} buyer={} invoice={} delivery={} lineItems={}",
                    trimmedOrderId,
                    toJson(form.buyer()),
                    toJson(form.invoice()),
                    toJson(form.delivery()),
                    toJson(form.lineItems()));
        }
        if (form.lineItems() == null || form.lineItems().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Zamówienie nie ma pozycji do zafakturowania.");
        }

        Instant boughtAt = invoiceMapper.earliestBoughtAt(form.lineItems());
        LocalDate transactionDate = boughtAt == null
                ? LocalDate.now(clock.withZone(ZONE))
                : boughtAt.atZone(ZONE).toLocalDate();
        LocalDate docDate = LocalDate.now(clock.withZone(ZONE));

        String uomName = unitsService.requireDefaultUnit().name();
        if (uomName == null || uomName.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Wybrana jednostka miary z Merit nie ma nazwy.");
        }
        uomName = uomName.trim();
        var taxes = taxesService.listTaxes();

        AllegroInvoiceMappingContext validationContext = new AllegroInvoiceMappingContext(
                null,
                "VALIDATE",
                docDate,
                transactionDate,
                taxes,
                uomName,
                account.getName(),
                sellerLogin,
                account.getPaymentMethod());
        invoiceMapper.toCreateInvoiceRequest(form, validationContext);

        String invoiceNo;
        try {
            invoiceNo = accountService.allocateInvoiceNo(accountId, docDate);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nie udało się zbudować numeru faktury.", ex);
        }

        CustomerResolution customer = resolveCustomer(form, createMissingCustomer);
        CreateInvoiceRequest request = invoiceMapper.toCreateInvoiceRequest(
                form,
                new AllegroInvoiceMappingContext(
                        customer.customerId(),
                        invoiceNo,
                        docDate,
                        transactionDate,
                        taxes,
                        uomName,
                        account.getName(),
                        sellerLogin,
                        account.getPaymentMethod()));
        return new PreparedAllegroInvoice(
                trimmedOrderId,
                request,
                customer.exists(),
                customer.toCreate());
    }

    private String fetchSellerLogin(String apiBaseUrl, String accessToken, String userAgent) {
        try {
            AllegroMe me = allegroApiClient.getMe(apiBaseUrl, accessToken, userAgent);
            if (me == null || me.login() == null || me.login().isBlank()) {
                return null;
            }
            return me.login().trim();
        } catch (RestClientResponseException ex) {
            org.springframework.http.HttpStatusCode status = ex.getStatusCode().is4xxClientError()
                    ? ex.getStatusCode()
                    : HttpStatus.BAD_GATEWAY;
            throw new ResponseStatusException(status, AllegroErrorMessages.from(ex), ex);
        }
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
            if (MeritErrorMessages.isCustomerExists(ex)) {
                String existingId = customersService.findCustomerId(createRequest);
                if (existingId != null && !existingId.isBlank()) {
                    return new CustomerResolution(existingId, true, createRequest);
                }
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Klient już istnieje w Merit, ale nie udało się go odnaleźć po NIP/nazwie.",
                        ex);
            }
            org.springframework.http.HttpStatusCode status = ex.getStatusCode().is4xxClientError()
                    ? ex.getStatusCode()
                    : HttpStatus.BAD_GATEWAY;
            throw new ResponseStatusException(status, "Nie udało się utworzyć klienta w Merit.", ex);
        }
    }

    private static String toJson(Object value) {
        if (value == null) {
            return "null";
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return String.valueOf(value);
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
