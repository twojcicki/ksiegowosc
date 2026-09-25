package pl.tw.ksiegowosc.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import pl.tw.ksiegowosc.dto.allegro.AllegroExternalId;
import pl.tw.ksiegowosc.dto.allegro.AllegroOfferReference;

class AllegroInvoiceMappingSupportTest {

    @Test
    void shouldBuildInvoiceNoAsPrefixSequenceMonthYear() {
        String invoiceNo = AllegroInvoiceMappingSupport.buildInvoiceNo("FS", 5, LocalDate.of(2026, 9, 6));

        assertThat(invoiceNo).isEqualTo("FS/5/09/2026");
    }

    @Test
    void shouldKeepUnpaddedSequenceAndPaddedMonth() {
        String invoiceNo = AllegroInvoiceMappingSupport.buildInvoiceNo("AL", 1, LocalDate.of(2026, 1, 15));

        assertThat(invoiceNo).isEqualTo("AL/1/01/2026");
    }

    @Test
    void shouldComputeNextSequenceAsInvoiceCountPlusOne() {
        assertThat(AllegroInvoiceMappingSupport.nextSequenceNumber(3)).isEqualTo(4);
    }

    @Test
    void shouldStartFromOneWhenNoInvoicesInMonth() {
        assertThat(AllegroInvoiceMappingSupport.nextSequenceNumber(0)).isEqualTo(1);
    }

    @Test
    void shouldRejectInvoiceNoExceedingMeritLimit() {
        assertThatThrownBy(() -> AllegroInvoiceMappingSupport.buildInvoiceNo(
                        "VERYLONGPREFIXABCDEFGH", 123456789, LocalDate.of(2026, 12, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("35");
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
    void shouldBuildHeaderCommentFromAllegroFields() {
        assertThat(AllegroInvoiceMappingSupport.resolveHeaderComment("order-1", "buyer1"))
                .isEqualTo("order-1 / buyer1");
        assertThat(AllegroInvoiceMappingSupport.resolveHeaderComment("order-1", null))
                .isEqualTo("order-1");
        assertThat(AllegroInvoiceMappingSupport.resolveHeaderComment(null, "buyer1"))
                .isEqualTo("buyer1");
    }
}
