package pl.tw.ksiegowosc.mapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import pl.tw.ksiegowosc.dto.allegro.AllegroOfferReference;

public final class AllegroInvoiceMappingSupport {

    public static final int ITEM_TYPE_STOCK = 1;
    public static final BigDecimal FALLBACK_VAT_PERCENT = new BigDecimal("23");
    private static final int ITEM_CODE_MAX = 20;

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MM");
    private static final DateTimeFormatter YEAR = DateTimeFormatter.ofPattern("yyyy");
    private static final int INVOICE_NO_MAX = 35;

    private AllegroInvoiceMappingSupport() {
    }

    public static String buildInvoiceNo(String orderId, LocalDate docDate) {
        String suffix = "/" + docDate.format(MONTH) + "/" + docDate.format(YEAR);
        int maxIdLen = INVOICE_NO_MAX - suffix.length();
        String compact = orderId.replace("-", "");
        if (compact.length() > maxIdLen) {
            compact = compact.substring(0, maxIdLen);
        }
        return compact + suffix;
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

    public static String resolveFooterComment(String vatRegNo, String orderId) {
        if (vatRegNo != null && !vatRegNo.isBlank()) {
            return vatRegNo.trim();
        }
        if (orderId != null && !orderId.isBlank()) {
            return orderId.trim();
        }
        return null;
    }
}
