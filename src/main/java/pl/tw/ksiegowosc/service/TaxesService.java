package pl.tw.ksiegowosc.service;

import java.math.BigDecimal;
import java.util.List;

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
        if (percent == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Brak stawki VAT.");
        }
        return listTaxes().stream()
                .filter(tax -> tax.taxPct() != null && tax.taxPct().compareTo(percent) == 0)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Brak stawki VAT " + percent.stripTrailingZeros().toPlainString()
                                + "% w Merit (Ustawienia → VAT)."));
    }

    public MeritTaxDto resolveByPercent(BigDecimal percent, List<MeritTaxDto> taxes) {
        if (percent == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Brak stawki VAT.");
        }
        List<MeritTaxDto> source = taxes == null ? List.of() : taxes;
        return source.stream()
                .filter(tax -> tax.taxPct() != null && tax.taxPct().compareTo(percent) == 0)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Brak stawki VAT " + percent.stripTrailingZeros().toPlainString()
                                + "% w Merit (Ustawienia → VAT)."));
    }

    public MeritTaxDto requireFallbackTax() {
        return resolveByPercent(FALLBACK_VAT_PERCENT);
    }

    public MeritTaxDto requireFallbackTax(List<MeritTaxDto> taxes) {
        return resolveByPercent(FALLBACK_VAT_PERCENT, taxes);
    }
}
