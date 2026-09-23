package pl.tw.ksiegowosc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import pl.tw.ksiegowosc.entity.AllegroAccount;
import pl.tw.ksiegowosc.mapper.MapperFixtures;

class AllegroOffersServiceTest {

    private AllegroApiClient allegroApiClient;
    private AllegroAuthService authService;
    private AllegroAccountService accountService;
    private AllegroOffersService offersService;

    @BeforeEach
    void setUp() {
        allegroApiClient = mock(AllegroApiClient.class);
        authService = mock(AllegroAuthService.class);
        accountService = mock(AllegroAccountService.class);
        offersService = new AllegroOffersService(
                allegroApiClient, authService, accountService, MapperFixtures.offerMapper());
    }

    @Test
    void shouldMapOffersToDtoFromAllConnectedAccounts() {
        AllegroAccount account = account(9L, "Sklep A");
        when(accountService.listConnectedAccounts()).thenReturn(List.of(account));
        when(authService.getValidAccessTokenForAccount(account)).thenReturn("token");
        when(allegroApiClient.getOffers("https://api.allegro.pl", "token", "ua", 0, 100, "ACTIVE"))
                .thenReturn(new AllegroOffersResponse(
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
        assertThat(offers.getFirst().accountId()).isEqualTo(9L);
        assertThat(offers.getFirst().accountName()).isEqualTo("Sklep A");
        assertThat(offers.getFirst().id()).isEqualTo("111");
        assertThat(offers.getFirst().name()).isEqualTo("Buty");
        assertThat(offers.getFirst().price()).isEqualByComparingTo(new BigDecimal("199.99"));
        assertThat(offers.getFirst().currency()).isEqualTo("PLN");
        assertThat(offers.getFirst().available()).isEqualTo(3);
        assertThat(offers.getFirst().sold()).isEqualTo(7);
        assertThat(offers.getFirst().publicationStatus()).isEqualTo("ACTIVE");
        verify(authService).getValidAccessTokenForAccount(account);
    }

    @Test
    void shouldSkipFailingAccountAndKeepOthers() {
        AllegroAccount ok = account(1L, "OK");
        AllegroAccount bad = account(2L, "BAD");
        when(accountService.listConnectedAccounts()).thenReturn(List.of(bad, ok));
        when(authService.getValidAccessTokenForAccount(bad)).thenThrow(new RuntimeException("boom"));
        when(authService.getValidAccessTokenForAccount(ok)).thenReturn("token");
        when(allegroApiClient.getOffers(eq("https://api.allegro.pl"), eq("token"), eq("ua"), eq(0), eq(100), any()))
                .thenReturn(new AllegroOffersResponse(
                List.of(new AllegroOfferItem(
                        "111",
                        "Buty",
                        new AllegroSellingMode("BUY_NOW", new AllegroPrice("10.00", "PLN")),
                        new AllegroStock(1, 0),
                        new AllegroPublication("ACTIVE"))),
                1,
                1));

        var offers = offersService.getOffers(0, 100, null);

        assertThat(offers).hasSize(1);
        assertThat(offers.getFirst().accountName()).isEqualTo("OK");
    }

    private static AllegroAccount account(Long id, String name) {
        AllegroAccount account = new AllegroAccount();
        account.setId(id);
        account.setName(name);
        account.setClientId("client-" + id);
        account.setClientSecret("secret");
        account.setApiBaseUrl("https://api.allegro.pl");
        account.setAuthUrl("https://allegro.pl");
        account.setUserAgent("ua");
        return account;
    }
}
