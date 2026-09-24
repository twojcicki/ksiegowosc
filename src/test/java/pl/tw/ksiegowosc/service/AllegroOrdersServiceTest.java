package pl.tw.ksiegowosc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;

import pl.tw.ksiegowosc.client.AllegroApiClient;
import pl.tw.ksiegowosc.dto.allegro.AllegroBuyer;
import pl.tw.ksiegowosc.dto.allegro.AllegroCheckoutForm;
import pl.tw.ksiegowosc.dto.allegro.AllegroCheckoutFormsResponse;
import pl.tw.ksiegowosc.dto.allegro.AllegroCheckoutSummary;
import pl.tw.ksiegowosc.dto.allegro.AllegroDelivery;
import pl.tw.ksiegowosc.dto.allegro.AllegroDeliveryMethod;
import pl.tw.ksiegowosc.dto.allegro.AllegroFulfillment;
import pl.tw.ksiegowosc.dto.allegro.AllegroLineItem;
import pl.tw.ksiegowosc.dto.allegro.AllegroOfferReference;
import pl.tw.ksiegowosc.dto.allegro.AllegroPrice;
import pl.tw.ksiegowosc.entity.AllegroAccount;
import pl.tw.ksiegowosc.entity.AllegroSoldInvoice;
import pl.tw.ksiegowosc.mapper.MapperFixtures;
import pl.tw.ksiegowosc.repository.AllegroSoldInvoiceRepository;

class AllegroOrdersServiceTest {

    private AllegroApiClient allegroApiClient;
    private AllegroAuthService authService;
    private AllegroAccountService accountService;
    private AllegroSoldInvoiceRepository soldInvoiceRepository;
    private AllegroOrdersService ordersService;

    @BeforeEach
    void setUp() {
        allegroApiClient = mock(AllegroApiClient.class);
        authService = mock(AllegroAuthService.class);
        accountService = mock(AllegroAccountService.class);
        soldInvoiceRepository = mock(AllegroSoldInvoiceRepository.class);
        ordersService = new AllegroOrdersService(
                allegroApiClient,
                authService,
                accountService,
                soldInvoiceRepository,
                MapperFixtures.soldItemMapper());
    }

    @Test
    void shouldAggregateCheckoutFormAsOneSoldOrder() {
        AllegroAccount account = account(5L, "Sklep");
        when(accountService.listConnectedAccounts()).thenReturn(List.of(account));
        when(authService.getValidAccessTokenForAccount(account)).thenReturn("token");
        when(soldInvoiceRepository.findByOrderIdIn(any())).thenReturn(List.of());
        when(allegroApiClient.getCheckoutForms(
                        eq("https://api.allegro.pl"),
                        eq("token"),
                        eq("ua"),
                        eq(0),
                        eq(100),
                        any(),
                        any()))
                .thenReturn(new AllegroCheckoutFormsResponse(
                        List.of(new AllegroCheckoutForm(
                                "order-1",
                                new AllegroBuyer("buyer1", null),
                                "READY_FOR_PROCESSING",
                                new AllegroFulfillment("SENT"),
                                null,
                                List.of(
                                        new AllegroLineItem(
                                                "line-1",
                                                new AllegroOfferReference("offer-1", "Książka", null),
                                                2,
                                                new AllegroPrice("25.00", "PLN"),
                                                null,
                                                Instant.parse("2026-01-10T08:00:00Z"),
                                                null),
                                        new AllegroLineItem(
                                                "line-2",
                                                new AllegroOfferReference("offer-2", "Długopis", null),
                                                1,
                                                new AllegroPrice("10.00", "PLN"),
                                                null,
                                                Instant.parse("2026-01-11T08:00:00Z"),
                                                null)),
                                new AllegroDelivery(
                                        new AllegroPrice("12.99", "PLN"),
                                        new AllegroDeliveryMethod("m1", "Kurier")),
                                new AllegroCheckoutSummary(new AllegroPrice("72.99", "PLN")),
                                null)),
                        1,
                        1));

        var result = ordersService.getSoldItems(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), 0, 100);

        assertThat(result.warnings()).isEmpty();
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().accountId()).isEqualTo(5L);
        assertThat(result.items().getFirst().accountName()).isEqualTo("Sklep");
        assertThat(result.items().getFirst().orderId()).isEqualTo("order-1");
        assertThat(result.items().getFirst().name()).isEqualTo("Książka (+1)");
        assertThat(result.items().getFirst().itemCount()).isEqualTo(2);
        assertThat(result.items().getFirst().totalGross()).isEqualByComparingTo(new BigDecimal("72.99"));
        assertThat(result.items().getFirst().boughtAt()).isEqualTo(Instant.parse("2026-01-10T08:00:00Z"));
        assertThat(result.items().getFirst().buyerLogin()).isEqualTo("buyer1");
        assertThat(result.items().getFirst().fulfillmentStatus()).isEqualTo("SENT");
        assertThat(result.items().getFirst().invoiceNo()).isNull();
        verify(authService).getValidAccessTokenForAccount(account);
    }

    @Test
    void shouldAttachInvoiceNoFromDatabase() {
        AllegroAccount account = account(5L, "Sklep");
        when(accountService.listConnectedAccounts()).thenReturn(List.of(account));
        when(authService.getValidAccessTokenForAccount(account)).thenReturn("token");
        AllegroSoldInvoice saved = new AllegroSoldInvoice();
        saved.setOrderId("order-1");
        saved.setInvoiceNo("order1/01/2026");
        when(soldInvoiceRepository.findByOrderIdIn(any())).thenReturn(List.of(saved));
        when(allegroApiClient.getCheckoutForms(
                        eq("https://api.allegro.pl"),
                        eq("token"),
                        eq("ua"),
                        eq(0),
                        eq(100),
                        any(),
                        any()))
                .thenReturn(new AllegroCheckoutFormsResponse(
                        List.of(new AllegroCheckoutForm(
                                "order-1",
                                new AllegroBuyer("buyer1", null),
                                "READY_FOR_PROCESSING",
                                new AllegroFulfillment("SENT"),
                                null,
                                List.of(new AllegroLineItem(
                                        "line-1",
                                        new AllegroOfferReference("offer-1", "Książka", null),
                                        1,
                                        new AllegroPrice("25.00", "PLN"),
                                        null,
                                        Instant.parse("2026-01-10T08:00:00Z"),
                                        null)),
                                null,
                                null,
                                null)),
                        1,
                        1));

        var result = ordersService.getSoldItems(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), 0, 100);

        assertThat(result.items().getFirst().invoiceNo()).isEqualTo("order1/01/2026");
    }

    @Test
    void shouldReturnAccessDeniedWarningWithoutFailingOtherAccounts() {
        AllegroAccount denied = account(5L, "Sklep A");
        AllegroAccount ok = account(6L, "Sklep B");
        when(accountService.listConnectedAccounts()).thenReturn(List.of(denied, ok));
        when(authService.getValidAccessTokenForAccount(denied)).thenReturn("token-a");
        when(authService.getValidAccessTokenForAccount(ok)).thenReturn("token-b");
        when(soldInvoiceRepository.findByOrderIdIn(any())).thenReturn(List.of());
        when(allegroApiClient.getCheckoutForms(
                        eq("https://api.allegro.pl"),
                        eq("token-a"),
                        eq("ua"),
                        eq(0),
                        eq(100),
                        any(),
                        any()))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.FORBIDDEN,
                        "Forbidden",
                        HttpHeaders.EMPTY,
                        "{\"errors\":[{\"code\":\"AccessDenied\"}]}".getBytes(),
                        null));
        when(allegroApiClient.getCheckoutForms(
                        eq("https://api.allegro.pl"),
                        eq("token-b"),
                        eq("ua"),
                        eq(0),
                        eq(100),
                        any(),
                        any()))
                .thenReturn(new AllegroCheckoutFormsResponse(
                        List.of(new AllegroCheckoutForm(
                                "order-2",
                                new AllegroBuyer("buyer2", null),
                                "READY_FOR_PROCESSING",
                                new AllegroFulfillment("SENT"),
                                null,
                                List.of(new AllegroLineItem(
                                        "line-1",
                                        new AllegroOfferReference("offer-1", "Buty", null),
                                        1,
                                        new AllegroPrice("100.00", "PLN"),
                                        null,
                                        Instant.parse("2026-01-10T08:00:00Z"),
                                        null)),
                                null,
                                null,
                                null)),
                        1,
                        1));

        var result = ordersService.getSoldItems(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), 0, 100);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().orderId()).isEqualTo("order-2");
        assertThat(result.warnings()).containsExactly(AllegroOrdersService.accessDeniedMessage("Sklep A"));
    }

    @Test
    void shouldRejectInvalidDateRange() {
        assertThatThrownBy(() -> ordersService.getSoldItems(
                        LocalDate.of(2026, 2, 1),
                        LocalDate.of(2026, 1, 1),
                        0,
                        100))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("późniejsza");
    }

    private static AllegroAccount account(Long id, String name) {
        AllegroAccount account = new AllegroAccount();
        account.setId(id);
        account.setName(name);
        account.setClientId("client");
        account.setClientSecret("secret");
        account.setApiBaseUrl("https://api.allegro.pl");
        account.setAuthUrl("https://allegro.pl");
        account.setUserAgent("ua");
        return account;
    }
}
