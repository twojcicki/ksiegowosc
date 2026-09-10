package pl.tw.ksiegowosc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import pl.tw.ksiegowosc.client.MeritApiClient;
import pl.tw.ksiegowosc.dto.MeritTaxDto;

class TaxesServiceTest {

    private MeritApiClient meritApiClient;
    private TaxesService taxesService;

    @BeforeEach
    void setUp() {
        meritApiClient = mock(MeritApiClient.class);
        taxesService = new TaxesService(meritApiClient);
    }

    @Test
    void shouldResolveTaxByPercentFromList() {
        List<MeritTaxDto> taxes = List.of(
                new MeritTaxDto("tax-8", "8", "VAT 8%", new BigDecimal("8.00")),
                new MeritTaxDto("tax-23", "23", "VAT 23%", new BigDecimal("23.00")));

        MeritTaxDto tax = taxesService.resolveByPercent(new BigDecimal("23"), taxes);

        assertThat(tax.id()).isEqualTo("tax-23");
    }

    @Test
    void shouldPreferSalesTaxWithMatchingCodeOverOtherSamePercent() {
        List<MeritTaxDto> taxes = List.of(
                new MeritTaxDto("74ea3b66-127c-4c25-be23-097b811dd23c", "WSTO23", "WSTO 23%", new BigDecimal("23")),
                new MeritTaxDto("tax-purchase", "Z23", "VAT zakup 23%", new BigDecimal("23")),
                new MeritTaxDto("tax-23", "23", "VAT 23%", new BigDecimal("23.00")));

        MeritTaxDto tax = taxesService.resolveByPercent(new BigDecimal("23.00"), taxes);

        assertThat(tax.id()).isEqualTo("tax-23");
        assertThat(tax.code()).isEqualTo("23");
    }

    @Test
    void shouldResolveFallbackTwentyThree() {
        when(meritApiClient.getTaxes()).thenReturn(List.of(
                new MeritTaxDto("tax-23", "23", "VAT 23%", new BigDecimal("23"))));

        MeritTaxDto tax = taxesService.requireFallbackTax();

        assertThat(tax.id()).isEqualTo("tax-23");
        assertThat(tax.taxPct()).isEqualByComparingTo(TaxesService.FALLBACK_VAT_PERCENT);
    }

    @Test
    void shouldFailWhenPercentMissingInMerit() {
        assertThatThrownBy(() -> taxesService.resolveByPercent(
                        new BigDecimal("5"),
                        List.of(new MeritTaxDto("tax-23", "23", "VAT 23%", new BigDecimal("23")))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("5%");
    }
}
