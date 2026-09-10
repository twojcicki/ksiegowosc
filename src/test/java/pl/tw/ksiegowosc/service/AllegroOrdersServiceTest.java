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
import org.springframework.web.server.ResponseStatusException;

import pl.tw.ksiegowosc.client.AllegroApiClient;
import pl.tw.ksiegowosc.dto.allegro.AllegroBuyer;
import pl.tw.ksiegowosc.dto.allegro.AllegroCheckoutForm;
import pl.tw.ksiegowosc.dto.allegro.AllegroCheckoutFormsResponse;
import pl.tw.ksiegowosc.dto.allegro.AllegroFulfillment;
import pl.tw.ksiegowosc.dto.allegro.AllegroLineItem;
import pl.tw.ksiegowosc.dto.allegro.AllegroOfferReference;
import pl.tw.ksiegowosc.dto.allegro.AllegroPrice;
import pl.tw.ksiegowosc.entity.AllegroSoldInvoice;
import pl.tw.ksiegowosc.mapper.MapperFixtures;
import pl.tw.ksiegowosc.repository.AllegroSoldInvoiceRepository;

class AllegroOrdersServiceTest {

    private AllegroApiClient allegroApiClient;
    private AllegroAuthService authService;
    private AllegroSoldInvoiceRepository soldInvoiceRepository;
    private AllegroOrdersService ordersService;

    @BeforeEach
    void setUp() {
        allegroApiClient = mock(AllegroApiClient.class);
        authService = mock(AllegroAuthService.class);
        soldInvoiceRepository = mock(AllegroSoldInvoiceRepository.class);
        ordersService = new AllegroOrdersService(
                allegroApiClient, authService, soldInvoiceRepository, MapperFixtures.soldItemMapper());
    }

    @Test
    void shouldAggregateCheckoutFormAsOneSoldOrder() {
        when(authService.getValidAccessToken()).thenReturn("token");
        when(soldInvoiceRepository.findByOrderIdIn(any())).thenReturn(List.of());
        when(allegroApiClient.getCheckoutForms(eq(0), eq(100), any(), any())).thenReturn(new AllegroCheckoutFormsResponse(
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
                                        Instant.parse("2026-01-10T08:00:00Z")),
                                new AllegroLineItem(
                                        "line-2",
                                        new AllegroOfferReference("offer-2", "Długopis", null),
                                        1,
                                        new AllegroPrice("10.00", "PLN"),
                                        null,
                                        Instant.parse("2026-01-11T08:00:00Z"))))),
                1,
                1));

        var items = ordersService.getSoldItems(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), 0, 100);

        assertThat(items).hasSize(1);
        assertThat(items.getFirst().orderId()).isEqualTo("order-1");
        assertThat(items.getFirst().name()).isEqualTo("Książka (+1)");
        assertThat(items.getFirst().itemCount()).isEqualTo(2);
        assertThat(items.getFirst().totalGross()).isEqualByComparingTo(new BigDecimal("60.00"));
        assertThat(items.getFirst().boughtAt()).isEqualTo(Instant.parse("2026-01-10T08:00:00Z"));
        assertThat(items.getFirst().buyerLogin()).isEqualTo("buyer1");
        assertThat(items.getFirst().fulfillmentStatus()).isEqualTo("SENT");
        assertThat(items.getFirst().invoiceNo()).isNull();
        verify(authService).getValidAccessToken();
    }

    @Test
    void shouldAttachInvoiceNoFromDatabase() {
        when(authService.getValidAccessToken()).thenReturn("token");
        AllegroSoldInvoice saved = new AllegroSoldInvoice();
        saved.setOrderId("order-1");
        saved.setInvoiceNo("order1/01/2026");
        when(soldInvoiceRepository.findByOrderIdIn(any())).thenReturn(List.of(saved));
        when(allegroApiClient.getCheckoutForms(eq(0), eq(100), any(), any())).thenReturn(new AllegroCheckoutFormsResponse(
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
                                Instant.parse("2026-01-10T08:00:00Z"))))),
                1,
                1));

        var items = ordersService.getSoldItems(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), 0, 100);

        assertThat(items.getFirst().invoiceNo()).isEqualTo("order1/01/2026");
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
}
