package pl.tw.ksiegowosc.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import pl.tw.ksiegowosc.dto.allegro.AllegroExternalId;
import pl.tw.ksiegowosc.dto.allegro.AllegroOfferReference;

class AllegroInvoiceMappingSupportTest {

    @Test
    void shouldBuildInvoiceNoWithinMeritLimit() {
        String orderId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
        String invoiceNo = AllegroInvoiceMappingSupport.buildInvoiceNo(orderId, LocalDate.of(2026, 9, 6));

        assertThat(invoiceNo).isEqualTo("a1b2c3d4e5f67890abcdef12345/09/2026");
        assertThat(invoiceNo.length()).isEqualTo(35);
    }

    @Test
    void shouldTruncateLongOrderIdInInvoiceNo() {
        String orderId = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee-ffff";
        String invoiceNo = AllegroInvoiceMappingSupport.buildInvoiceNo(orderId, LocalDate.of(2026, 1, 15));

        assertThat(invoiceNo).endsWith("/01/2026");
        assertThat(invoiceNo.length()).isEqualTo(35);
    }

    @Test
    void shouldConvertGrossToNetWithGivenVatRate() {
        BigDecimal rate = AllegroInvoiceMappingSupport.vatRateFromPercent(new BigDecimal("23"));
        assertThat(AllegroInvoiceMappingSupport.toNet(new BigDecimal("25.00"), rate))
                .isEqualByComparingTo(new BigDecimal("20.33"));
    }

    @Test
    void shouldConvertGrossToNetWithEightPercent() {
        BigDecimal rate = AllegroInvoiceMappingSupport.vatRateFromPercent(new BigDecimal("8"));
        assertThat(AllegroInvoiceMappingSupport.toNet(new BigDecimal("108.00"), rate))
                .isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    void shouldPreferExternalIdAsItemCode() {
        assertThat(AllegroInvoiceMappingSupport.resolveItemCode(
                        new AllegroOfferReference("123", "Name", new AllegroExternalId("SKU-9"))))
                .isEqualTo("SKU-9");
    }

    @Test
    void shouldUseOfferIdWhenExternalMissing() {
        assertThat(AllegroInvoiceMappingSupport.resolveItemCode(
                        new AllegroOfferReference("12345678901234567890EXTRA", "Name", null)))
                .isEqualTo("12345678901234567890");
    }

    @Test
    void shouldBuildCommentsFromAllegroFields() {
        assertThat(AllegroInvoiceMappingSupport.resolveHeaderComment("order-1", "buyer1"))
                .isEqualTo("order-1 / buyer1");
        assertThat(AllegroInvoiceMappingSupport.resolveFooterComment("5252674798", "order-1"))
                .isEqualTo("5252674798");
        assertThat(AllegroInvoiceMappingSupport.resolveFooterComment(null, "order-1"))
                .isEqualTo("order-1");
    }
}
