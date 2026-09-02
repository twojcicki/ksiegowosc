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
import pl.tw.ksiegowosc.dto.allegro.AllegroPrice;

class AllegroOrdersServiceTest {

    private AllegroApiClient allegroApiClient;
    private AllegroAuthService authService;
    private AllegroOrdersService ordersService;

    @BeforeEach
    void setUp() {
        allegroApiClient = mock(AllegroApiClient.class);
        authService = mock(AllegroAuthService.class);
        ordersService = new AllegroOrdersService(allegroApiClient, authService);
    }

    @Test
    void shouldFlattenCheckoutFormsToSoldItems() {
        when(authService.getValidAccessToken()).thenReturn("token");
        when(allegroApiClient.getCheckoutForms(eq(0), eq(100), any(), any())).thenReturn(new AllegroCheckoutFormsResponse(
                List.of(new AllegroCheckoutForm(
                        "order-1",
                        new AllegroBuyer("buyer1"),
                        "READY_FOR_PROCESSING",
                        new AllegroFulfillment("SENT"),
                        List.of(new AllegroLineItem(
                                "line-1",
                                "offer-1",
                                "Książka",
                                2,
                                new AllegroPrice("25.00", "PLN"),
                                Instant.parse("2026-01-10T08:00:00Z"))))),
                1,
                1));

        var items = ordersService.getSoldItems(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), 0, 100);

        assertThat(items).hasSize(1);
        assertThat(items.getFirst().orderId()).isEqualTo("order-1");
        assertThat(items.getFirst().name()).isEqualTo("Książka");
        assertThat(items.getFirst().quantity()).isEqualTo(2);
        assertThat(items.getFirst().price()).isEqualByComparingTo(new BigDecimal("25.00"));
        assertThat(items.getFirst().buyerLogin()).isEqualTo("buyer1");
        assertThat(items.getFirst().fulfillmentStatus()).isEqualTo("SENT");
        verify(authService).getValidAccessToken();
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
