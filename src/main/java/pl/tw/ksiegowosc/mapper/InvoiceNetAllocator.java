package pl.tw.ksiegowosc.mapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Wylicza netto pozycji (2 dp) z brutto pozycji tak, aby suma netto grupy (jedna stawka VAT)
 * była dokładnie równa {@code round(suma brutto / (1+VAT), 2)}.
 *
 * <p>Merit zaokrągla każdy wiersz do groszy i porównuje sumę wierszy z {@code TotalAmount},
 * więc każdy wiersz musi mieć netto z dokładnością do grosza, a ich suma musi się zgadzać.
 * Brakujące grosze (0..n) trafiają na pozycje z największą resztą po obcięciu do groszy.
 */
public final class InvoiceNetAllocator {

    private static final BigDecimal GROSZ = new BigDecimal("0.01");
    private static final int EXACT_SCALE = 12;

    private InvoiceNetAllocator() {
    }

    public static List<BigDecimal> allocateNet(List<BigDecimal> lineGross, BigDecimal vatRate) {
        int size = lineGross.size();
        if (size == 0) {
            return List.of();
        }
        BigDecimal divisor = BigDecimal.ONE.add(vatRate);
        BigDecimal[] exact = new BigDecimal[size];
        BigDecimal[] net = new BigDecimal[size];
        BigDecimal grossSum = BigDecimal.ZERO;
        BigDecimal netSum = BigDecimal.ZERO;
        for (int i = 0; i < size; i++) {
            BigDecimal gross = lineGross.get(i).setScale(2, RoundingMode.HALF_UP);
            grossSum = grossSum.add(gross);
            exact[i] = gross.divide(divisor, EXACT_SCALE, RoundingMode.HALF_UP);
            net[i] = exact[i].setScale(2, RoundingMode.FLOOR);
            netSum = netSum.add(net[i]);
        }
        BigDecimal target = grossSum.divide(divisor, 2, RoundingMode.HALF_UP);
        int missing = target.subtract(netSum).divide(GROSZ, 0, RoundingMode.HALF_UP).intValueExact();
        if (missing < 0 || missing > size) {
            throw new IllegalStateException(
                    "Nie można rozdzielić netto: brakujące grosze=" + missing + ", pozycji=" + size);
        }

        List<Integer> order = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            order.add(i);
        }
        order.sort(Comparator
                .comparing((Integer i) -> exact[i].subtract(net[i])).reversed()
                .thenComparing(i -> i));
        for (int k = 0; k < missing; k++) {
            int index = order.get(k);
            net[index] = net[index].add(GROSZ);
        }

        List<BigDecimal> result = new ArrayList<>(size);
        for (BigDecimal value : net) {
            result.add(value);
        }
        return result;
    }
}
