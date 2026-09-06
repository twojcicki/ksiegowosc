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
import java.time.LocalDate;
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
import pl.tw.ksiegowosc.dto.allegro.AllegroBuyer;
import pl.tw.ksiegowosc.dto.allegro.AllegroCheckoutForm;
import pl.tw.ksiegowosc.dto.allegro.AllegroFulfillment;
import pl.tw.ksiegowosc.dto.allegro.AllegroInvoice;
import pl.tw.ksiegowosc.dto.allegro.AllegroInvoiceAddress;
import pl.tw.ksiegowosc.dto.allegro.AllegroInvoiceCompany;
import pl.tw.ksiegowosc.dto.allegro.AllegroLineItem;
import pl.tw.ksiegowosc.dto.allegro.AllegroPrice;
import pl.tw.ksiegowosc.dto.allegro.AllegroTaxId;
import pl.tw.ksiegowosc.entity.AllegroSoldInvoice;
import pl.tw.ksiegowosc.repository.AllegroSoldInvoiceRepository;

class AllegroInvoiceServiceTest {

    private AllegroApiClient allegroApiClient;
    private AllegroAuthService authService;
    private CustomersService customersService;
    private InvoicesService invoicesService;
    private AllegroSoldInvoiceRepository soldInvoiceRepository;
    private AllegroInvoiceService invoiceService;

    @BeforeEach
    void setUp() {
        allegroApiClient = mock(AllegroApiClient.class);
        authService = mock(AllegroAuthService.class);
        customersService = mock(CustomersService.class);
        invoicesService = mock(InvoicesService.class);
        soldInvoiceRepository = mock(AllegroSoldInvoiceRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-09-06T12:00:00Z"), ZoneOffset.UTC);
        invoiceService = new AllegroInvoiceService(
                allegroApiClient,
                authService,
                customersService,
                invoicesService,
                soldInvoiceRepository,
                clock);
    }

    @Test
    void shouldBuildInvoiceNoWithinMeritLimit() {
        String orderId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
        String invoiceNo = AllegroInvoiceService.buildInvoiceNo(orderId, LocalDate.of(2026, 9, 6));

        assertThat(invoiceNo).isEqualTo("a1b2c3d4e5f67890abcdef12345/09/2026");
        assertThat(invoiceNo.length()).isEqualTo(35);
    }

    @Test
    void shouldTruncateLongOrderIdInInvoiceNo() {
        String orderId = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee-ffff";
        String invoiceNo = AllegroInvoiceService.buildInvoiceNo(orderId, LocalDate.of(2026, 1, 15));

        assertThat(invoiceNo).endsWith("/01/2026");
        assertThat(invoiceNo.length()).isEqualTo(35);
    }

    @Test
    void shouldIssueInvoiceForAllLineItemsUsingExistingCustomer() {
        when(soldInvoiceRepository.existsById("order-1")).thenReturn(false);
        when(authService.getValidAccessToken()).thenReturn("token");
        when(allegroApiClient.getCheckoutForm("order-1")).thenReturn(sampleForm());
        when(customersService.getCustomersByVatRegNo("5252674798")).thenReturn(List.of(
                new CustomerDto("cust-1", "Firma", null, "5252674798", null, null, null, "PLN")));
        when(invoicesService.createInvoice(any())).thenReturn(new CreateInvoiceResponse("merit-inv-1", "cust-1"));

        var response = invoiceService.issueInvoice("order-1");

        assertThat(response.invoiceNo()).isEqualTo("order1/01/2026");
        assertThat(response.meritInvoiceId()).isEqualTo("merit-inv-1");

        ArgumentCaptor<CreateInvoiceRequest> requestCaptor = ArgumentCaptor.forClass(CreateInvoiceRequest.class);
        verify(invoicesService).createInvoice(requestCaptor.capture());
        CreateInvoiceRequest request = requestCaptor.getValue();
        assertThat(request.customerId()).isEqualTo("cust-1");
        assertThat(request.lines()).hasSize(2);
        assertThat(request.lines().getFirst().price()).isEqualByComparingTo(new BigDecimal("20.33"));
        assertThat(request.taxAmounts()).hasSize(1);
        assertThat(request.totalAmount()).isEqualByComparingTo(new BigDecimal("48.79"));

        ArgumentCaptor<AllegroSoldInvoice> entityCaptor = ArgumentCaptor.forClass(AllegroSoldInvoice.class);
        verify(soldInvoiceRepository).save(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getOrderId()).isEqualTo("order-1");
        assertThat(entityCaptor.getValue().getInvoiceNo()).isEqualTo("order1/01/2026");
        verify(customersService, never()).createCustomer(any());
    }

    @Test
    void shouldCreateCustomerWhenNotFoundByVat() {
        when(soldInvoiceRepository.existsById("order-1")).thenReturn(false);
        when(authService.getValidAccessToken()).thenReturn("token");
        when(allegroApiClient.getCheckoutForm("order-1")).thenReturn(sampleForm());
        when(customersService.getCustomersByVatRegNo("5252674798")).thenReturn(List.of());
        when(customersService.createCustomer(any())).thenReturn(new MeritCreateCustomerResponse("new-cust", "Allegro"));
        when(invoicesService.createInvoice(any())).thenReturn(new CreateInvoiceResponse("merit-inv-1", "new-cust"));

        invoiceService.issueInvoice("order-1");

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

        assertThatThrownBy(() -> invoiceService.issueInvoice("order-1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("już wystawiona");

        verify(allegroApiClient, never()).getCheckoutForm(any());
        verify(invoicesService, never()).createInvoice(any());
    }

    private static AllegroCheckoutForm sampleForm() {
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
                                "offer-1",
                                "Książka",
                                1,
                                new AllegroPrice("25.00", "PLN"),
                                Instant.parse("2026-01-10T08:00:00Z")),
                        new AllegroLineItem(
                                "line-2",
                                "offer-2",
                                "Długopis",
                                1,
                                new AllegroPrice("35.00", "PLN"),
                                Instant.parse("2026-01-11T08:00:00Z"))));
    }
}
