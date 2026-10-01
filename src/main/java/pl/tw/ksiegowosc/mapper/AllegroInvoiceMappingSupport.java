package pl.tw.ksiegowosc.mapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import pl.tw.ksiegowosc.dto.allegro.AllegroOfferReference;

public final class AllegroInvoiceMappingSupport {

    public static final int ITEM_TYPE_STOCK = 1;
    public static final int ITEM_TYPE_SERVICE = 2;
    public static final int UNIT_NET_SCALE = 7;
    public static final BigDecimal FALLBACK_VAT_PERCENT = new BigDecimal("23");
    public static final String FALLBACK_DELIVERY_CODE = "Dostawa";
    public static final String FALLBACK_SURCHARGE_CODE = "DOPLATA";
    private static final int ITEM_CODE_MAX = 20;

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MM");
    private static final DateTimeFormatter YEAR = DateTimeFormatter.ofPattern("yyyy");
    private static final int INVOICE_NO_MAX = 35;

    private AllegroInvoiceMappingSupport() {
    }

    public static String buildInvoiceNo(String prefix, int sequenceNumber, LocalDate docDate) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix is required");
        }
        if (sequenceNumber < 1) {
            throw new IllegalArgumentException("sequenceNumber must be >= 1");
        }
        String invoiceNo = prefix.trim()
                + "/"
                + sequenceNumber
                + "/"
                + docDate.format(MONTH)
                + "/"
                + docDate.format(YEAR);
        if (invoiceNo.length() > INVOICE_NO_MAX) {
            throw new IllegalArgumentException("InvoiceNo exceeds Merit limit of " + INVOICE_NO_MAX);
        }
        return invoiceNo;
    }

    public static int nextSequenceNumber(int invoiceCountInMonth) {
        if (invoiceCountInMonth < 0) {
            throw new IllegalArgumentException("invoiceCountInMonth must be >= 0");
        }
        return invoiceCountInMonth + 1;
    }

    public static BigDecimal toNet(BigDecimal gross, BigDecimal vatRate) {
        return toNet(gross, vatRate, 2);
    }

    public static BigDecimal toNet(BigDecimal gross, BigDecimal vatRate, int scale) {
        BigDecimal divisor = BigDecimal.ONE.add(vatRate);
        return gross.divide(divisor, scale, RoundingMode.HALF_UP);
    }

    /**
     * Netto pod model Merit: VAT = round(netto × stawka, 2), brutto = netto + VAT.
     * Start: round(G/(1+r), 2); jeśli Merit brutto ≠ G, spróbuj netto − 0,01 (bliższy / nie zawyżać).
     */
    public static BigDecimal meritCompatibleNet(BigDecimal targetGross, BigDecimal vatRate) {
        if (targetGross == null || vatRate == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal gross = targetGross.setScale(2, RoundingMode.HALF_UP);
        BigDecimal n = toNet(gross, vatRate, 2);
        BigDecimal meritGross = meritGrossFromNet(n, vatRate);
        if (meritGross.compareTo(gross) == 0) {
            return n;
        }
        BigDecimal n2 = n.subtract(new BigDecimal("0.01")).setScale(2, RoundingMode.HALF_UP);
        if (n2.compareTo(BigDecimal.ZERO) <= 0) {
            return n;
        }
        BigDecimal meritGross2 = meritGrossFromNet(n2, vatRate);
        int diff1 = meritGross.subtract(gross).abs().compareTo(meritGross2.subtract(gross).abs());
        if (diff1 < 0) {
            return n;
        }
        if (diff1 > 0) {
            return n2;
        }
        // remis: preferuj wariant z brutto ≤ cel (nie zawyżać)
        if (meritGross2.compareTo(gross) <= 0) {
            return n2;
        }
        if (meritGross.compareTo(gross) <= 0) {
            return n;
        }
        return n2;
    }

    public static BigDecimal meritGrossFromNet(BigDecimal net, BigDecimal vatRate) {
        if (net == null || vatRate == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal n = net.setScale(2, RoundingMode.HALF_UP);
        BigDecimal vat = n.multiply(vatRate).setScale(2, RoundingMode.HALF_UP);
        return n.add(vat);
    }

    public static BigDecimal lineNetFromUnitNet(BigDecimal unitNet, int quantity) {
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be >= 1");
        }
        return unitNet.multiply(BigDecimal.valueOf(quantity)).setScale(UNIT_NET_SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal unitNetFromLineNet(BigDecimal lineNet, int quantity) {
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be >= 1");
        }
        return lineNet.divide(BigDecimal.valueOf(quantity), UNIT_NET_SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal vatRateFromPercent(BigDecimal percent) {
        return percent.divide(new BigDecimal("100"), 8, RoundingMode.HALF_UP);
    }

    public static String resolveItemCode(AllegroOfferReference offer) {
        if (offer != null
                && offer.external() != null
                && offer.external().id() != null
                && !offer.external().id().isBlank()) {
            return MappingSupport.truncate(offer.external().id().trim(), ITEM_CODE_MAX);
        }
        if (offer != null && offer.id() != null && !offer.id().isBlank()) {
            return MappingSupport.truncate(offer.id().trim(), ITEM_CODE_MAX);
        }
        return null;
    }

    public static String resolveDescription(AllegroOfferReference offer) {
        if (offer == null || offer.name() == null || offer.name().isBlank()) {
            return null;
        }
        return offer.name().trim();
    }

    public static String resolveHeaderComment(
            String accountName, String sellerLogin, String orderId, String buyerLogin) {
        String transaction = resolveTransactionPart(orderId, buyerLogin);
        if (transaction == null) {
            return null;
        }

        StringBuilder comment = new StringBuilder();
        appendSegment(comment, accountName);
        appendSegment(comment, sellerLogin);
        if (comment.length() > 0) {
            comment.append(", ");
        }
        comment.append("ID transakcji: ").append(transaction);
        return comment.toString();
    }

    private static String resolveTransactionPart(String orderId, String buyerLogin) {
        if (orderId == null || orderId.isBlank()) {
            return buyerLogin == null || buyerLogin.isBlank() ? null : buyerLogin.trim();
        }
        if (buyerLogin == null || buyerLogin.isBlank()) {
            return orderId.trim();
        }
        return orderId.trim() + " / " + buyerLogin.trim();
    }

    private static void appendSegment(StringBuilder comment, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (comment.length() > 0) {
            comment.append(", ");
        }
        comment.append(value.trim());
    }

    public static String truncateItemCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return MappingSupport.truncate(value.trim(), ITEM_CODE_MAX);
    }
}
