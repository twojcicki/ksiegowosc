package pl.tw.ksiegowosc.service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import pl.tw.ksiegowosc.client.MeritApiClient;
import pl.tw.ksiegowosc.dto.MeritTaxDto;
import pl.tw.ksiegowosc.mapper.AllegroInvoiceMappingSupport;

@Service
public class TaxesService {

    public static final BigDecimal FALLBACK_VAT_PERCENT = AllegroInvoiceMappingSupport.FALLBACK_VAT_PERCENT;

    private final MeritApiClient meritApiClient;

    public TaxesService(MeritApiClient meritApiClient) {
        this.meritApiClient = meritApiClient;
    }

    public List<MeritTaxDto> listTaxes() {
        return meritApiClient.getTaxes();
    }

    public MeritTaxDto resolveByPercent(BigDecimal percent) {
        return resolveByPercent(percent, listTaxes());
    }

    public MeritTaxDto resolveByPercent(BigDecimal percent, List<MeritTaxDto> taxes) {
        if (percent == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Brak stawki VAT.");
        }
        List<MeritTaxDto> source = taxes == null ? List.of() : taxes;
        String codeHint = percent.stripTrailingZeros().toPlainString();

        List<MeritTaxDto> matches = source.stream()
                .filter(tax -> tax != null)
                .filter(tax -> tax.id() != null && !tax.id().isBlank())
                .filter(tax -> tax.taxPct() != null && tax.taxPct().compareTo(percent) == 0)
                .filter(tax -> !looksLikePurchaseTax(tax))
                .sorted(preferredSalesTaxOrder(codeHint))
                .toList();

        if (matches.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Brak stawki VAT " + codeHint + "% w Merit (Ustawienia → VAT).");
        }
        return matches.getFirst();
    }

    public MeritTaxDto requireFallbackTax() {
        return resolveByPercent(FALLBACK_VAT_PERCENT);
    }

    public MeritTaxDto requireFallbackTax(List<MeritTaxDto> taxes) {
        return resolveByPercent(FALLBACK_VAT_PERCENT, taxes);
    }

    /**
     * Prefer sales VAT whose Code matches the percent (e.g. "23"), then plain numeric codes,
     * and avoid purchase/input VAT names that Merit may list in gettaxes but reject on sales invoices.
     */
    private static Comparator<MeritTaxDto> preferredSalesTaxOrder(String codeHint) {
        return Comparator
                .comparing((MeritTaxDto tax) -> !codeMatchesPercent(tax.code(), codeHint))
                .thenComparing(tax -> !isPlainPercentCode(tax.code()))
                .thenComparing(tax -> tax.code() == null ? "" : tax.code());
    }

    private static boolean codeMatchesPercent(String code, String codeHint) {
        String normalized = normalizeTaxCode(code);
        return codeHint.equals(normalized) || (codeHint + "%").equalsIgnoreCase(normalized);
    }

    private static boolean isPlainPercentCode(String code) {
        String normalized = normalizeTaxCode(code);
        if (normalized.isEmpty()) {
            return false;
        }
        String withoutPercent = normalized.endsWith("%")
                ? normalized.substring(0, normalized.length() - 1)
                : normalized;
        try {
            new BigDecimal(withoutPercent);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static String normalizeTaxCode(String code) {
        if (code == null) {
            return "";
        }
        return code.trim().replace(" ", "").toLowerCase(Locale.ROOT);
    }

    private static boolean looksLikePurchaseTax(MeritTaxDto tax) {
        String name = tax.name() == null ? "" : tax.name().toLowerCase(Locale.ROOT);
        String code = tax.code() == null ? "" : tax.code().toLowerCase(Locale.ROOT);
        return name.contains("zakup")
                || name.contains("naliczon")
                || name.contains("input")
                || name.contains("purchase")
                || code.contains("zakup");
    }
}
