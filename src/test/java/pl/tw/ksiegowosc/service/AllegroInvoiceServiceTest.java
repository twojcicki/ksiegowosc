package pl.tw.ksiegowosc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import pl.tw.ksiegowosc.client.AllegroApiClient;
import pl.tw.ksiegowosc.dto.CreateInvoiceRequest;
import pl.tw.ksiegowosc.dto.CreateInvoiceResponse;
import pl.tw.ksiegowosc.dto.CustomerDto;
import pl.tw.ksiegowosc.dto.MeritCreateCustomerRequest;
import pl.tw.ksiegowosc.dto.MeritCreateCustomerResponse;
import pl.tw.ksiegowosc.dto.MeritUnitDto;
import pl.tw.ksiegowosc.dto.allegro.AllegroBuyer;
import pl.tw.ksiegowosc.dto.allegro.AllegroCheckoutForm;
import pl.tw.ksiegowosc.dto.allegro.AllegroCheckoutSummary;
import pl.tw.ksiegowosc.dto.allegro.AllegroDelivery;
import pl.tw.ksiegowosc.dto.allegro.AllegroDeliveryAddress;
import pl.tw.ksiegowosc.dto.allegro.AllegroDeliveryMethod;
import pl.tw.ksiegowosc.dto.allegro.AllegroFulfillment;
import pl.tw.ksiegowosc.dto.allegro.AllegroInvoice;
import pl.tw.ksiegowosc.dto.allegro.AllegroInvoiceAddress;
import pl.tw.ksiegowosc.dto.allegro.AllegroInvoiceCompany;
import pl.tw.ksiegowosc.dto.allegro.AllegroExternalId;
import pl.tw.ksiegowosc.dto.allegro.AllegroLineItem;
import pl.tw.ksiegowosc.dto.allegro.AllegroLineItemTax;
import pl.tw.ksiegowosc.dto.allegro.AllegroMe;
import pl.tw.ksiegowosc.dto.allegro.AllegroNaturalPerson;
import pl.tw.ksiegowosc.dto.allegro.AllegroOfferReference;
import pl.tw.ksiegowosc.dto.allegro.AllegroPrice;
import pl.tw.ksiegowosc.dto.allegro.AllegroSurcharge;
import pl.tw.ksiegowosc.dto.allegro.AllegroTaxId;
import pl.tw.ksiegowosc.entity.AllegroAccount;
import pl.tw.ksiegowosc.entity.AllegroSoldInvoice;
import pl.tw.ksiegowosc.mapper.MapperFixtures;
import pl.tw.ksiegowosc.repository.AllegroSoldInvoiceRepository;

class AllegroInvoiceServiceTest {

    private AllegroApiClient allegroApiClient;
    private AllegroAuthService authService;
    private AllegroAccountService accountService;
    private CustomersService customersService;
    private InvoicesService invoicesService;
    private TaxesService taxesService;
    private UnitsService unitsService;
    private AllegroSoldInvoiceRepository soldInvoiceRepository;
    private AllegroInvoiceService invoiceService;

    @BeforeEach
    void setUp() {
        allegroApiClient = mock(AllegroApiClient.class);
        authService = mock(AllegroAuthService.class);
        accountService = mock(AllegroAccountService.class);
        customersService = mock(CustomersService.class);
        invoicesService = mock(InvoicesService.class);
        taxesService = mock(TaxesService.class);
        unitsService = mock(UnitsService.class);
        soldInvoiceRepository = mock(AllegroSoldInvoiceRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-09-06T12:00:00Z"), ZoneOffset.UTC);
        when(taxesService.listTaxes()).thenReturn(MapperFixtures.sampleTaxes());
        when(unitsService.requireDefaultUnit()).thenReturn(new MeritUnitDto("SZT", "szt."));
        when(accountService.allocateInvoiceNo(any(), any())).thenReturn("FS/1/01/2026");
        AllegroAccount account = sampleAccount(9L);
        when(accountService.requireOwnedAccount(9L)).thenReturn(account);
        when(authService.getValidAccessTokenForAccount(account)).thenReturn("token");
        when(allegroApiClient.getMe("https://api.allegro.pl", "token", "ua"))
                .thenReturn(new AllegroMe("123", "elfabric_pl"));
        invoiceService = new AllegroInvoiceService(
                allegroApiClient,
                authService,
                accountService,
                customersService,
                invoicesService,
                taxesService,
                unitsService,
                soldInvoiceRepository,
                MapperFixtures.billingMapper(),
                MapperFixtures.invoiceMapper(),
                MapperFixtures.meritInvoiceMapper(),
                MapperFixtures.soldInvoiceMapper(),
                clock);
    }

    @Test
    void shouldIssueInvoiceForAllLineItemsUsingExistingCustomer() {
        when(soldInvoiceRepository.existsById("order-1")).thenReturn(false);
        when(allegroApiClient.getCheckoutForm("https://api.allegro.pl", "token", "ua", "order-1")).thenReturn(sampleForm(null, null));
        when(customersService.getCustomersByVatRegNo("5252674798")).thenReturn(List.of(
                new CustomerDto("cust-1", "Firma", null, "5252674798", null, null, null, "PLN")));
        when(invoicesService.createInvoice(any())).thenReturn(new CreateInvoiceResponse("merit-inv-1", "cust-1"));

        var response = invoiceService.issueInvoice(9L, "order-1");

        assertThat(response.invoiceNo()).isEqualTo("FS/1/01/2026");
        assertThat(response.meritInvoiceId()).isEqualTo("merit-inv-1");

        ArgumentCaptor<CreateInvoiceRequest> requestCaptor = ArgumentCaptor.forClass(CreateInvoiceRequest.class);
        verify(invoicesService).createInvoice(requestCaptor.capture());
        CreateInvoiceRequest request = requestCaptor.getValue();
        assertThat(request.customerId()).isEqualTo("cust-1");
        assertThat(request.lines()).hasSize(2);
        assertThat(request.lines().getFirst().price()).isEqualByComparingTo(new BigDecimal("20.33"));
        assertThat(request.lines().getFirst().itemCode()).isEqualTo("SKU-BOOK");
        assertThat(request.lines().getFirst().description()).isEqualTo("Książka");
        assertThat(request.lines().getFirst().itemType()).isEqualTo(1);
        assertThat(request.lines().getFirst().uomName()).isEqualTo("szt.");
        assertThat(request.lines().getFirst().taxId()).isEqualTo("tax-23");
        assertThat(request.headerComment()).isEqualTo("Sklep, elfabric_pl, ID transakcji: order-1 / buyer1");
        assertThat(request.footerComment()).isNull();
        assertThat(request.taxAmounts()).hasSize(1);
        assertThat(request.taxAmounts().getFirst().taxId()).isEqualTo("tax-23");
        assertThat(request.totalAmount()).isEqualByComparingTo(new BigDecimal("48.79"));

        ArgumentCaptor<AllegroSoldInvoice> entityCaptor = ArgumentCaptor.forClass(AllegroSoldInvoice.class);
        verify(soldInvoiceRepository).save(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getOrderId()).isEqualTo("order-1");
        assertThat(entityCaptor.getValue().getInvoiceNo()).isEqualTo("FS/1/01/2026");
        verify(customersService, never()).createCustomer(any());
        verify(taxesService).listTaxes();
        verify(accountService).allocateInvoiceNo(9L, java.time.LocalDate.of(2026, 1, 10));
    }

    @Test
    void shouldUseAllegroTaxRateAndGroupTaxAmounts() {
        when(soldInvoiceRepository.existsById("order-1")).thenReturn(false);
        when(allegroApiClient.getCheckoutForm("https://api.allegro.pl", "token", "ua", "order-1")).thenReturn(sampleForm(
                new AllegroLineItemTax("23.00", "GOODS", null),
                new AllegroLineItemTax("8.00", "GOODS", null)));
        when(customersService.getCustomersByVatRegNo("5252674798")).thenReturn(List.of(
                new CustomerDto("cust-1", "Firma", null, "5252674798", null, null, null, "PLN")));
        when(invoicesService.createInvoice(any())).thenReturn(new CreateInvoiceResponse("merit-inv-1", "cust-1"));

        invoiceService.issueInvoice(9L, "order-1");

        ArgumentCaptor<CreateInvoiceRequest> requestCaptor = ArgumentCaptor.forClass(CreateInvoiceRequest.class);
        verify(invoicesService).createInvoice(requestCaptor.capture());
        CreateInvoiceRequest request = requestCaptor.getValue();
        assertThat(request.lines().getFirst().taxId()).isEqualTo("tax-23");
        assertThat(request.lines().get(1).taxId()).isEqualTo("tax-8");
        assertThat(request.taxAmounts()).hasSize(2);
        assertThat(request.taxAmounts())
                .extracting(t -> t.taxId())
                .containsExactly("tax-23", "tax-8");
    }

    @Test
    void shouldCreateCustomerWhenNotFoundByVat() {
        when(soldInvoiceRepository.existsById("order-1")).thenReturn(false);
        when(allegroApiClient.getCheckoutForm("https://api.allegro.pl", "token", "ua", "order-1")).thenReturn(sampleForm(null, null));
        when(customersService.getCustomersByVatRegNo("5252674798")).thenReturn(List.of());
        when(customersService.createCustomer(any())).thenReturn(new MeritCreateCustomerResponse("new-cust", "Allegro"));
        when(invoicesService.createInvoice(any())).thenReturn(new CreateInvoiceResponse("merit-inv-1", "new-cust"));

        invoiceService.issueInvoice(9L, "order-1");

        ArgumentCaptor<MeritCreateCustomerRequest> customerCaptor =
                ArgumentCaptor.forClass(MeritCreateCustomerRequest.class);
        verify(customersService).createCustomer(customerCaptor.capture());
        assertThat(customerCaptor.getValue().name()).isEqualTo("Allegro Sp. z o.o.");
        assertThat(customerCaptor.getValue().vatRegNo()).isEqualTo("5252674798");
        assertThat(customerCaptor.getValue().countryCode()).isEqualTo("PL");
    }

    @Test
    void shouldRejectAlreadyInvoicedOrder() {
        when(soldInvoiceRepository.existsById("order-1")).thenReturn(true);

        assertThatThrownBy(() -> invoiceService.issueInvoice(9L, "order-1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("już wystawiona");

        verify(allegroApiClient, never()).getCheckoutForm(any(), any(), any(), any());
        verify(invoicesService, never()).createInvoice(any());
    }

    @Test
    void shouldPreviewInvoiceWithoutSendingOrCreatingCustomer() {
        when(allegroApiClient.getCheckoutForm("https://api.allegro.pl", "token", "ua", "order-1")).thenReturn(sampleForm(null, null));
        when(customersService.getCustomersByVatRegNo("5252674798")).thenReturn(List.of(
                new CustomerDto("cust-1", "Firma", null, "5252674798", null, null, null, "PLN")));

        var preview = invoiceService.previewInvoice(9L, "order-1");

        assertThat(preview.orderId()).isEqualTo("order-1");
        assertThat(preview.customerExists()).isTrue();
        assertThat(preview.customerId()).isEqualTo("cust-1");
        assertThat(preview.rows())
                .anySatisfy(row -> {
                    assertThat(row.meritField()).isEqualTo("InvoiceNo");
                    assertThat(row.value()).isEqualTo("FS/1/01/2026");
                })
                .anySatisfy(row -> {
                    assertThat(row.meritField()).isEqualTo("InvoiceRow[0].Item.Code");
                    assertThat(row.value()).isEqualTo("SKU-BOOK");
                })
                .anySatisfy(row -> {
                    assertThat(row.meritField()).isEqualTo("InvoiceRow[0].Item.Description");
                    assertThat(row.value()).isEqualTo("Książka");
                })
                .anySatisfy(row -> {
                    assertThat(row.meritField()).isEqualTo("InvoiceRow[0].Item.Type");
                    assertThat(row.value()).isEqualTo("1");
                })
                .anySatisfy(row -> {
                    assertThat(row.meritField()).isEqualTo("InvoiceRow[0].Item.UOMName");
                    assertThat(row.value()).isEqualTo("szt.");
                });

        verify(invoicesService, never()).createInvoice(any());
        verify(customersService, never()).createCustomer(any());
        verify(soldInvoiceRepository, never()).save(any());
    }

    @Test
    void shouldPreviewCustomerCreatePayloadWhenCustomerMissing() {
        when(allegroApiClient.getCheckoutForm("https://api.allegro.pl", "token", "ua", "order-1")).thenReturn(sampleForm(null, null));
        when(customersService.getCustomersByVatRegNo("5252674798")).thenReturn(List.of());

        var preview = invoiceService.previewInvoice(9L, "order-1");

        assertThat(preview.customerExists()).isFalse();
        assertThat(preview.customerId()).isNull();
        assertThat(preview.rows())
                .anySatisfy(row -> {
                    assertThat(row.meritField()).isEqualTo("Name");
                    assertThat(row.value()).isEqualTo("Allegro Sp. z o.o.");
                })
                .anySatisfy(row -> {
                    assertThat(row.meritField()).isEqualTo("VatRegNo");
                    assertThat(row.value()).isEqualTo("5252674798");
                })
                .anySatisfy(row -> {
                    assertThat(row.meritField()).isEqualTo("Customer.Id");
                    assertThat(row.value()).contains("utworzony");
                });

        verify(customersService, never()).createCustomer(any());
        verify(invoicesService, never()).createInvoice(any());
    }

    @Test
    void shouldAddDeliveryLineAndMatchTotalToPay() {
        when(soldInvoiceRepository.existsById("order-1")).thenReturn(false);
        AllegroDelivery delivery = new AllegroDelivery(
                new AllegroPrice("10.00", "PLN"),
                new AllegroDeliveryMethod("ship-method-1", "Paczkomat"),
                null);
        AllegroCheckoutSummary summary = new AllegroCheckoutSummary(new AllegroPrice("70.00", "PLN"));
        when(allegroApiClient.getCheckoutForm("https://api.allegro.pl", "token", "ua", "order-1"))
                .thenReturn(sampleForm(null, null, delivery, summary, null));
        when(customersService.getCustomersByVatRegNo("5252674798")).thenReturn(List.of(
                new CustomerDto("cust-1", "Firma", null, "5252674798", null, null, null, "PLN")));
        when(invoicesService.createInvoice(any())).thenReturn(new CreateInvoiceResponse("merit-inv-1", "cust-1"));

        invoiceService.issueInvoice(9L, "order-1");

        ArgumentCaptor<CreateInvoiceRequest> requestCaptor = ArgumentCaptor.forClass(CreateInvoiceRequest.class);
        verify(invoicesService).createInvoice(requestCaptor.capture());
        CreateInvoiceRequest request = requestCaptor.getValue();
        assertThat(request.lines()).hasSize(3);
        assertThat(request.lines().get(2).itemCode()).isEqualTo("ship-method-1");
        assertThat(request.lines().get(2).description()).isEqualTo("Paczkomat");
        assertThat(request.lines().get(2).itemType()).isEqualTo(2);
        assertThat(request.lines().get(2).price()).isEqualByComparingTo(new BigDecimal("8.13"));
        assertThat(request.lines().get(2).taxId()).isEqualTo("tax-23");
        BigDecimal invoiceGross = request.totalAmount()
                .add(request.taxAmounts().stream()
                        .map(t -> t.amount())
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
        assertThat(invoiceGross).isEqualByComparingTo(new BigDecimal("70.00"));
    }

    @Test
    void shouldRejectWhenInvoiceGrossDoesNotMatchTotalToPay() {
        when(soldInvoiceRepository.existsById("order-1")).thenReturn(false);
        AllegroCheckoutSummary summary = new AllegroCheckoutSummary(new AllegroPrice("99.00", "PLN"));
        when(allegroApiClient.getCheckoutForm("https://api.allegro.pl", "token", "ua", "order-1"))
                .thenReturn(sampleForm(null, null, null, summary, null));
        when(customersService.getCustomersByVatRegNo("5252674798")).thenReturn(List.of(
                new CustomerDto("cust-1", "Firma", null, "5252674798", null, null, null, "PLN")));

        assertThatThrownBy(() -> invoiceService.issueInvoice(9L, "order-1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("summary.totalToPay");

        verify(invoicesService, never()).createInvoice(any());
    }

    @Test
    void shouldReusePrivateCustomerByExactName() {
        when(soldInvoiceRepository.existsById("order-1")).thenReturn(false);
        when(allegroApiClient.getCheckoutForm("https://api.allegro.pl", "token", "ua", "order-1"))
                .thenReturn(privatePersonForm());
        when(customersService.findCustomerByExactName("Anna Nowak")).thenReturn(java.util.Optional.of(
                new CustomerDto("cust-priv", "Anna Nowak", null, null, null, null, null, "PLN")));
        when(invoicesService.createInvoice(any())).thenReturn(new CreateInvoiceResponse("merit-inv-1", "cust-priv"));

        var response = invoiceService.issueInvoice(9L, "order-1");

        assertThat(response.meritInvoiceId()).isEqualTo("merit-inv-1");
        verify(customersService, never()).createCustomer(any());
        ArgumentCaptor<CreateInvoiceRequest> requestCaptor = ArgumentCaptor.forClass(CreateInvoiceRequest.class);
        verify(invoicesService).createInvoice(requestCaptor.capture());
        assertThat(requestCaptor.getValue().customerId()).isEqualTo("cust-priv");
        assertThat(requestCaptor.getValue().footerComment()).isNull();
    }

    @Test
    void shouldCreateKlientAllegroWhenNoNaturalPerson() {
        when(soldInvoiceRepository.existsById("order-1")).thenReturn(false);
        when(allegroApiClient.getCheckoutForm("https://api.allegro.pl", "token", "ua", "order-1"))
                .thenReturn(noInvoiceAddressForm());
        when(customersService.findCustomerByExactName("Klient Allegro (buyer1)"))
                .thenReturn(java.util.Optional.empty());
        when(customersService.createCustomer(any())).thenReturn(new MeritCreateCustomerResponse("cust-new", "Klient Allegro (buyer1)"));
        when(invoicesService.createInvoice(any())).thenReturn(new CreateInvoiceResponse("merit-inv-1", "cust-new"));

        invoiceService.issueInvoice(9L, "order-1");

        ArgumentCaptor<MeritCreateCustomerRequest> customerCaptor =
                ArgumentCaptor.forClass(MeritCreateCustomerRequest.class);
        verify(customersService).createCustomer(customerCaptor.capture());
        assertThat(customerCaptor.getValue().name()).isEqualTo("Klient Allegro (buyer1)");
        assertThat(customerCaptor.getValue().notTdCustomer()).isTrue();
        assertThat(customerCaptor.getValue().vatRegNo()).isNull();
        assertThat(customerCaptor.getValue().address()).isEqualTo("Dostawcza 5");
        assertThat(customerCaptor.getValue().city()).isEqualTo("Wrocław");
    }

    private static AllegroAccount sampleAccount(Long id) {
        AllegroAccount account = new AllegroAccount();
        account.setId(id);
        account.setName("Sklep");
        account.setClientId("cid");
        account.setClientSecret("secret");
        account.setInvoicePrefix("FS");
        account.setApiBaseUrl("https://api.allegro.pl");
        account.setAuthUrl("https://allegro.pl");
        account.setUserAgent("ua");
        return account;
    }

    private static AllegroCheckoutForm privatePersonForm() {
        return new AllegroCheckoutForm(
                "order-1",
                new AllegroBuyer("buyer1", "buyer@example.com"),
                "READY_FOR_PROCESSING",
                new AllegroFulfillment("SENT"),
                new AllegroInvoice(
                        true,
                        new AllegroInvoiceAddress(
                                "Fakturowa 1",
                                "Kraków",
                                "30-001",
                                "PL",
                                null,
                                new AllegroNaturalPerson("Anna", "Nowak"))),
                List.of(
                        new AllegroLineItem(
                                "line-1",
                                new AllegroOfferReference("offer-1", "Książka", new AllegroExternalId("SKU-BOOK")),
                                1,
                                new AllegroPrice("25.00", "PLN"),
                                null,
                                Instant.parse("2026-01-10T08:00:00Z"),
                                null)),
                null,
                new AllegroCheckoutSummary(new AllegroPrice("25.00", "PLN")),
                null);
    }

    private static AllegroCheckoutForm noInvoiceAddressForm() {
        return new AllegroCheckoutForm(
                "order-1",
                new AllegroBuyer("buyer1", "buyer@example.com"),
                "READY_FOR_PROCESSING",
                new AllegroFulfillment("SENT"),
                new AllegroInvoice(false, null),
                List.of(
                        new AllegroLineItem(
                                "line-1",
                                new AllegroOfferReference("offer-1", "Książka", new AllegroExternalId("SKU-BOOK")),
                                1,
                                new AllegroPrice("25.00", "PLN"),
                                null,
                                Instant.parse("2026-01-10T08:00:00Z"),
                                null)),
                new AllegroDelivery(
                        new AllegroPrice("0.00", "PLN"),
                        new AllegroDeliveryMethod("m1", "Kurier"),
                        new AllegroDeliveryAddress(
                                "Jan", "Kowalski", "Dostawcza 5", "Wrocław", "50-001", "PL", null)),
                new AllegroCheckoutSummary(new AllegroPrice("25.00", "PLN")),
                null);
    }

    private static AllegroCheckoutForm sampleForm(AllegroLineItemTax tax1, AllegroLineItemTax tax2) {
        return sampleForm(tax1, tax2, null, null, null);
    }

    private static AllegroCheckoutForm sampleForm(
            AllegroLineItemTax tax1,
            AllegroLineItemTax tax2,
            AllegroDelivery delivery,
            AllegroCheckoutSummary summary,
            List<AllegroSurcharge> surcharges) {
        return new AllegroCheckoutForm(
                "order-1",
                new AllegroBuyer("buyer1", "buyer@example.com"),
                "READY_FOR_PROCESSING",
                new AllegroFulfillment("SENT"),
                new AllegroInvoice(
                        true,
                        new AllegroInvoiceAddress(
                                "Grunwaldzka 182",
                                "Poznań",
                                "60-166",
                                "PL",
                                new AllegroInvoiceCompany(
                                        "Allegro Sp. z o.o.",
                                        "5252674798",
                                        List.of(new AllegroTaxId("PL_NIP", "5252674798"))),
                                null)),
                List.of(
                        new AllegroLineItem(
                                "line-1",
                                new AllegroOfferReference(
                                        "offer-1",
                                        "Książka",
                                        new AllegroExternalId("SKU-BOOK")),
                                1,
                                new AllegroPrice("25.00", "PLN"),
                                tax1,
                                Instant.parse("2026-01-10T08:00:00Z"),
                                null),
                        new AllegroLineItem(
                                "line-2",
                                new AllegroOfferReference("offer-2", "Długopis", null),
                                1,
                                new AllegroPrice("35.00", "PLN"),
                                tax2,
                                Instant.parse("2026-01-11T08:00:00Z"),
                                null)),
                delivery,
                summary,
                surcharges);
    }
}
