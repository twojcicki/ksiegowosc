package pl.tw.ksiegowosc.mapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import pl.tw.ksiegowosc.dto.allegro.AllegroOfferReference;

public final class AllegroInvoiceMappingSupport {

    public static final int ITEM_TYPE_STOCK = 1;
    public static final int ITEM_TYPE_SERVICE = 2;
    public static final BigDecimal FALLBACK_VAT_PERCENT = new BigDecimal("23");
    public static final String FALLBACK_DELIVERY_CODE = "DOSTAWA";
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
        BigDecimal divisor = BigDecimal.ONE.add(vatRate);
        return gross.divide(divisor, 2, RoundingMode.HALF_UP);
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

    public static String resolveHeaderComment(String orderId, String buyerLogin) {
        if (orderId == null || orderId.isBlank()) {
            return buyerLogin == null || buyerLogin.isBlank() ? null : buyerLogin.trim();
        }
        if (buyerLogin == null || buyerLogin.isBlank()) {
            return orderId.trim();
        }
        return orderId.trim() + " / " + buyerLogin.trim();
    }

    public static String truncateItemCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return MappingSupport.truncate(value.trim(), ITEM_CODE_MAX);
    }
}
