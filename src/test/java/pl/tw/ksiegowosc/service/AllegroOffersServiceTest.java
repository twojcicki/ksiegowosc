package pl.tw.ksiegowosc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pl.tw.ksiegowosc.client.AllegroApiClient;
import pl.tw.ksiegowosc.dto.allegro.AllegroOfferItem;
import pl.tw.ksiegowosc.dto.allegro.AllegroOffersResponse;
import pl.tw.ksiegowosc.dto.allegro.AllegroPrice;
import pl.tw.ksiegowosc.dto.allegro.AllegroPublication;
import pl.tw.ksiegowosc.dto.allegro.AllegroSellingMode;
import pl.tw.ksiegowosc.dto.allegro.AllegroStock;

class AllegroOffersServiceTest {

    private AllegroApiClient allegroApiClient;
    private AllegroAuthService authService;
    private AllegroOffersService offersService;

    @BeforeEach
    void setUp() {
        allegroApiClient = mock(AllegroApiClient.class);
        authService = mock(AllegroAuthService.class);
        offersService = new AllegroOffersService(allegroApiClient, authService);
    }

    @Test
    void shouldMapOffersToDto() {
        when(authService.getValidAccessToken()).thenReturn("token");
        when(allegroApiClient.getOffers(0, 100, "ACTIVE")).thenReturn(new AllegroOffersResponse(
                List.of(new AllegroOfferItem(
                        "111",
                        "Buty",
                        new AllegroSellingMode("BUY_NOW", new AllegroPrice("199.99", "PLN")),
                        new AllegroStock(3, 7),
                        new AllegroPublication("ACTIVE"))),
                1,
                1));

        var offers = offersService.getOffers(0, 100, "ACTIVE");

        assertThat(offers).hasSize(1);
        assertThat(offers.getFirst().id()).isEqualTo("111");
        assertThat(offers.getFirst().name()).isEqualTo("Buty");
        assertThat(offers.getFirst().price()).isEqualByComparingTo(new BigDecimal("199.99"));
        assertThat(offers.getFirst().currency()).isEqualTo("PLN");
        assertThat(offers.getFirst().available()).isEqualTo(3);
        assertThat(offers.getFirst().sold()).isEqualTo(7);
        assertThat(offers.getFirst().publicationStatus()).isEqualTo("ACTIVE");
        verify(authService).getValidAccessToken();
    }
}
