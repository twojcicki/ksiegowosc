package pl.tw.ksiegowosc.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

class InvoiceNetAllocatorTest {

    private static final BigDecimal VAT_23 = new BigDecimal("0.23");

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void shouldAddMissingGroszToLineWithLargestRemainder() {
        // zamówienie z Merit: towar 403,26 + dostawa 10,95 = 414,21; suma netto musi dać 336,76
        List<BigDecimal> net = InvoiceNetAllocator.allocateNet(List.of(bd("403.26"), bd("10.95")), VAT_23);

        assertThat(net).containsExactly(bd("327.86"), bd("8.90"));
        assertThat(sum(net)).isEqualByComparingTo("336.76");
    }

    @Test
    void shouldKeepSumEqualToNetOfGroupGross() {
        List<BigDecimal> net = InvoiceNetAllocator.allocateNet(List.of(bd("25.00"), bd("35.00"), bd("10.00")), VAT_23);

        assertThat(net).containsExactly(bd("20.32"), bd("28.46"), bd("8.13"));
        assertThat(sum(net)).isEqualByComparingTo("56.91");
    }

    @Test
    void shouldNotDriftWhenRoundingEachLineSeparately() {
        // 3 × 0,99: osobne zaokrąglenie dałoby 3 × 0,80 = 2,40, a poprawne netto całości to 2,41
        List<BigDecimal> net = InvoiceNetAllocator.allocateNet(List.of(bd("0.99"), bd("0.99"), bd("0.99")), VAT_23);

        assertThat(sum(net)).isEqualByComparingTo("2.41");
    }

    @Test
    void shouldReturnEmptyListForNoLines() {
        assertThat(InvoiceNetAllocator.allocateNet(List.of(), VAT_23)).isEmpty();
    }

    @Test
    void shouldHandleZeroVatRate() {
        List<BigDecimal> net = InvoiceNetAllocator.allocateNet(List.of(bd("10.01"), bd("5.50")), BigDecimal.ZERO);

        assertThat(net).containsExactly(bd("10.01"), bd("5.50"));
    }

    @Test
    void shouldKeepInvariantsForRandomOrders() {
        Random random = new Random(11);
        BigDecimal[] rates = {bd("0.23"), bd("0.08"), bd("0.05"), BigDecimal.ZERO};
        for (int order = 0; order < 5_000; order++) {
            BigDecimal rate = rates[random.nextInt(rates.length)];
            int lineCount = 1 + random.nextInt(6);
            List<BigDecimal> gross = new java.util.ArrayList<>();
            BigDecimal grossSum = BigDecimal.ZERO;
            for (int i = 0; i < lineCount; i++) {
                BigDecimal line = BigDecimal.valueOf(random.nextInt(60_000) + 1, 2)
                        .multiply(BigDecimal.valueOf(1 + random.nextInt(5)));
                gross.add(line);
                grossSum = grossSum.add(line);
            }

            List<BigDecimal> net = InvoiceNetAllocator.allocateNet(gross, rate);

            BigDecimal target = grossSum.divide(BigDecimal.ONE.add(rate), 2, RoundingMode.HALF_UP);
            assertThat(sum(net)).isEqualByComparingTo(target);
            for (int i = 0; i < lineCount; i++) {
                BigDecimal exact = gross.get(i).divide(BigDecimal.ONE.add(rate), 12, RoundingMode.HALF_UP);
                assertThat(net.get(i).subtract(exact).abs()).isLessThan(bd("0.01"));
            }
            // korekta Merit nigdy większa niż 1 grosz na stawkę
            BigDecimal meritGross = AllegroInvoiceMappingSupport.meritGrossFromNet(sum(net), rate);
            assertThat(grossSum.subtract(meritGross).abs()).isLessThanOrEqualTo(bd("0.01"));
        }
    }
}
