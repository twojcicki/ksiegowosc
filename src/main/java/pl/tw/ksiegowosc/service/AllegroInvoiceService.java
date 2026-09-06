package pl.tw.ksiegowosc.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import pl.tw.ksiegowosc.client.AllegroApiClient;
import pl.tw.ksiegowosc.client.AllegroErrorMessages;
import pl.tw.ksiegowosc.dto.CreateInvoiceLineRequest;
import pl.tw.ksiegowosc.dto.CreateInvoiceRequest;
import pl.tw.ksiegowosc.dto.CreateInvoiceResponse;
import pl.tw.ksiegowosc.dto.CreateInvoiceTaxAmountRequest;
import pl.tw.ksiegowosc.dto.CustomerDto;
import pl.tw.ksiegowosc.dto.IssueAllegroInvoiceResponse;
import pl.tw.ksiegowosc.dto.MeritCreateCustomerRequest;
import pl.tw.ksiegowosc.dto.MeritCreateCustomerResponse;
import pl.tw.ksiegowosc.dto.allegro.AllegroBuyer;
import pl.tw.ksiegowosc.dto.allegro.AllegroCheckoutForm;
import pl.tw.ksiegowosc.dto.allegro.AllegroInvoice;
import pl.tw.ksiegowosc.dto.allegro.AllegroInvoiceAddress;
import pl.tw.ksiegowosc.dto.allegro.AllegroInvoiceCompany;
import pl.tw.ksiegowosc.dto.allegro.AllegroLineItem;
import pl.tw.ksiegowosc.dto.allegro.AllegroNaturalPerson;
import pl.tw.ksiegowosc.dto.allegro.AllegroPrice;
import pl.tw.ksiegowosc.dto.allegro.AllegroTaxId;
import pl.tw.ksiegowosc.entity.AllegroSoldInvoice;
import pl.tw.ksiegowosc.repository.AllegroSoldInvoiceRepository;

@Service
public class AllegroInvoiceService {

    static final String DEFAULT_TAX_ID = "973a4395-665f-47a6-a5b6-5384dd24f8d0";
    static final int ITEM_TYPE_SERVICE = 2;
    static final BigDecimal VAT_RATE = new BigDecimal("0.23");
    private static final BigDecimal VAT_DIVISOR = BigDecimal.ONE.add(VAT_RATE);
    private static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MM");
    private static final DateTimeFormatter YEAR = DateTimeFormatter.ofPattern("yyyy");
    private static final int INVOICE_NO_MAX = 35;

    private final AllegroApiClient allegroApiClient;
    private final AllegroAuthService authService;
    private final CustomersService customersService;
    private final InvoicesService invoicesService;
    private final AllegroSoldInvoiceRepository soldInvoiceRepository;
    private final Clock clock;

    public AllegroInvoiceService(
            AllegroApiClient allegroApiClient,
            AllegroAuthService authService,
            CustomersService customersService,
            InvoicesService invoicesService,
            AllegroSoldInvoiceRepository soldInvoiceRepository,
            Clock clock) {
        this.allegroApiClient = allegroApiClient;
        this.authService = authService;
        this.customersService = customersService;
        this.invoicesService = invoicesService;
        this.soldInvoiceRepository = soldInvoiceRepository;
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

        Instant boughtAt = earliestBoughtAt(form.lineItems());
        LocalDate docDate = boughtAt == null
                ? LocalDate.now(clock.withZone(ZONE))
                : boughtAt.atZone(ZONE).toLocalDate();
        String invoiceNo = buildInvoiceNo(trimmedOrderId, docDate);

        String customerId = resolveCustomerId(form);
        CreateInvoiceRequest request = buildInvoiceRequest(form, customerId, invoiceNo, docDate);
        CreateInvoiceResponse created = invoicesService.createInvoice(request);

        AllegroSoldInvoice entity = new AllegroSoldInvoice();
        entity.setOrderId(trimmedOrderId);
        entity.setInvoiceNo(invoiceNo);
        entity.setMeritInvoiceId(created.invoiceId());
        entity.setCreatedAt(Instant.now(clock));
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
        BuyerBilling billing = extractBilling(form);
        if (billing.vatRegNo() != null && !billing.vatRegNo().isBlank()) {
            List<CustomerDto> existing = customersService.getCustomersByVatRegNo(billing.vatRegNo());
            if (!existing.isEmpty() && existing.getFirst().customerId() != null) {
                return existing.getFirst().customerId();
            }
        }

        MeritCreateCustomerRequest createRequest = new MeritCreateCustomerRequest(
                billing.name(),
                billing.notTdCustomer(),
                billing.countryCode(),
                blankToNull(billing.vatRegNo()),
                blankToNull(billing.address()),
                blankToNull(billing.city()),
                blankToNull(billing.postalCode()),
                blankToNull(billing.email()),
                "PLN",
                "PL");
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

    private CreateInvoiceRequest buildInvoiceRequest(
            AllegroCheckoutForm form,
            String customerId,
            String invoiceNo,
            LocalDate docDate) {
        List<CreateInvoiceLineRequest> lines = new ArrayList<>();
        BigDecimal totalNet = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalVat = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        String currency = "PLN";

        for (AllegroLineItem lineItem : form.lineItems()) {
            AllegroPrice price = lineItem.price();
            BigDecimal unitGross = parseAmount(price == null ? null : price.amount());
            if (unitGross == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pozycja zamówienia nie ma ceny.");
            }
            if (price != null && price.currency() != null && !price.currency().isBlank()) {
                currency = price.currency();
            }
            int qty = lineItem.quantity() == null ? 1 : lineItem.quantity();
            BigDecimal unitNet = toNet(unitGross);
            BigDecimal lineNet = unitNet.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineGross = unitGross.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineVat = lineGross.subtract(lineNet);

            totalNet = totalNet.add(lineNet);
            totalVat = totalVat.add(lineVat);

            String itemCode = lineItem.offerId() == null || lineItem.offerId().isBlank()
                    ? "ALLEGRO"
                    : truncate(lineItem.offerId().replace("-", ""), 20);
            String description = lineItem.name() == null || lineItem.name().isBlank()
                    ? "Pozycja Allegro"
                    : lineItem.name();

            lines.add(new CreateInvoiceLineRequest(
                    itemCode,
                    description,
                    ITEM_TYPE_SERVICE,
                    BigDecimal.valueOf(qty),
                    unitNet,
                    DEFAULT_TAX_ID));
        }

        BuyerBilling billing = extractBilling(form);
        String headerComment = "Allegro " + form.id()
                + (billing.login() == null ? "" : " / " + billing.login());
        String footerComment = billing.vatRegNo() == null || billing.vatRegNo().isBlank()
                ? "Zamówienie Allegro"
                : "NIP kupującego: " + billing.vatRegNo();

        return new CreateInvoiceRequest(
                customerId,
                invoiceNo,
                docDate,
                docDate.plusDays(14),
                currency,
                headerComment,
                footerComment,
                totalNet,
                lines,
                List.of(new CreateInvoiceTaxAmountRequest(DEFAULT_TAX_ID, totalVat)));
    }

    static String buildInvoiceNo(String orderId, LocalDate docDate) {
        String suffix = "/" + docDate.format(MONTH) + "/" + docDate.format(YEAR);
        int maxIdLen = INVOICE_NO_MAX - suffix.length();
        String compact = orderId.replace("-", "");
        if (compact.length() > maxIdLen) {
            compact = compact.substring(0, maxIdLen);
        }
        return compact + suffix;
    }

    static BigDecimal toNet(BigDecimal gross) {
        return gross.divide(VAT_DIVISOR, 2, RoundingMode.HALF_UP);
    }

    private static Instant earliestBoughtAt(List<AllegroLineItem> lineItems) {
        return lineItems.stream()
                .map(AllegroLineItem::boughtAt)
                .filter(Objects::nonNull)
                .min(Instant::compareTo)
                .orElse(null);
    }

    private static BuyerBilling extractBilling(AllegroCheckoutForm form) {
        AllegroBuyer buyer = form.buyer();
        String login = buyer == null ? null : buyer.login();
        String email = buyer == null ? null : buyer.email();

        AllegroInvoice invoice = form.invoice();
        AllegroInvoiceAddress address = invoice == null ? null : invoice.address();
        AllegroInvoiceCompany company = address == null ? null : address.company();
        AllegroNaturalPerson person = address == null ? null : address.naturalPerson();

        String vatRegNo = resolveVatRegNo(company);
        String name;
        boolean notTdCustomer;
        if (company != null && company.name() != null && !company.name().isBlank()) {
            name = company.name().trim();
            notTdCustomer = vatRegNo == null || vatRegNo.isBlank();
        } else if (person != null) {
            name = ((person.firstName() == null ? "" : person.firstName().trim()) + " "
                    + (person.lastName() == null ? "" : person.lastName().trim())).trim();
            notTdCustomer = true;
        } else if (login != null && !login.isBlank()) {
            name = login.trim();
            notTdCustomer = true;
        } else {
            name = "Klient Allegro";
            notTdCustomer = true;
        }

        String countryCode = address != null && address.countryCode() != null && !address.countryCode().isBlank()
                ? address.countryCode().trim()
                : "PL";

        return new BuyerBilling(
                name,
                notTdCustomer,
                countryCode,
                vatRegNo,
                address == null ? null : address.street(),
                address == null ? null : address.city(),
                address == null ? null : address.zipCode(),
                email,
                login);
    }

    private static String resolveVatRegNo(AllegroInvoiceCompany company) {
        if (company == null) {
            return null;
        }
        if (company.ids() != null) {
            for (AllegroTaxId id : company.ids()) {
                if (id == null || id.value() == null || id.value().isBlank()) {
                    continue;
                }
                if (id.type() == null || "PL_NIP".equalsIgnoreCase(id.type()) || "OTHER".equalsIgnoreCase(id.type())) {
                    return id.value().trim();
                }
            }
            for (AllegroTaxId id : company.ids()) {
                if (id != null && id.value() != null && !id.value().isBlank()) {
                    return id.value().trim();
                }
            }
        }
        if (company.taxId() != null && !company.taxId().isBlank()) {
            return company.taxId().trim();
        }
        return null;
    }

    private static BigDecimal parseAmount(String amount) {
        if (amount == null || amount.isBlank()) {
            return null;
        }
        return new BigDecimal(amount);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String truncate(String value, int max) {
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    private record BuyerBilling(
            String name,
            boolean notTdCustomer,
            String countryCode,
            String vatRegNo,
            String address,
            String city,
            String postalCode,
            String email,
            String login) {
    }
}
