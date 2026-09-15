package pl.tw.ksiegowosc.mapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import pl.tw.ksiegowosc.dto.BuyerBilling;
import pl.tw.ksiegowosc.dto.CreateInvoiceLineRequest;
import pl.tw.ksiegowosc.dto.CreateInvoiceRequest;
import pl.tw.ksiegowosc.dto.CreateInvoiceTaxAmountRequest;
import pl.tw.ksiegowosc.dto.MeritTaxDto;
import pl.tw.ksiegowosc.dto.allegro.AllegroCheckoutForm;
import pl.tw.ksiegowosc.dto.allegro.AllegroLineItem;
import pl.tw.ksiegowosc.dto.allegro.AllegroLineItemTax;
import pl.tw.ksiegowosc.dto.allegro.AllegroOfferReference;
import pl.tw.ksiegowosc.dto.allegro.AllegroPrice;
import pl.tw.ksiegowosc.service.TaxesService;

/**
 * Jedno miejsce budujące CreateInvoiceRequest z Allegro — pola zgodne z {@link AllegroMeritInvoiceMappings#RULES}.
 */
@Component
public class AllegroToMeritInvoiceBuilder {

    private final AllegroBillingMapper billingMapper;
    private final TaxesService taxesService;

    public AllegroToMeritInvoiceBuilder(AllegroBillingMapper billingMapper, TaxesService taxesService) {
        this.billingMapper = billingMapper;
        this.taxesService = taxesService;
    }

    public CreateInvoiceRequest toCreateInvoiceRequest(
            AllegroCheckoutForm form,
            AllegroInvoiceMappingContext context) {
        List<CreateInvoiceLineRequest> lines = new ArrayList<>();
        BigDecimal totalNet = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        Map<String, BigDecimal> vatByTaxId = new LinkedHashMap<>();
        String currency = "PLN";

        for (AllegroLineItem lineItem : form.lineItems()) {
            AllegroPrice price = lineItem.price();
            BigDecimal unitGross = parseAmount(price == null ? null : price.amount());
            if (unitGross == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pozycja zamówienia nie ma ceny.");
            }
            if (price != null && price.currency() != null && !price.currency().isBlank()) {
                currency = price.currency();
            }

            MeritTaxDto tax = resolveTax(lineItem, context.taxes());
            BigDecimal vatRate = AllegroInvoiceMappingSupport.vatRateFromPercent(tax.taxPct());
            int qty = resolveQuantity(lineItem);
            BigDecimal unitNet = resolveUnitNet(unitGross, vatRate);
            BigDecimal lineNet = unitNet.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineGross = unitGross.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineVat = lineGross.subtract(lineNet);

            totalNet = totalNet.add(lineNet);
            vatByTaxId.merge(tax.id(), lineVat, BigDecimal::add);

            String itemCode = resolveItemCode(lineItem.offer());
            if (itemCode == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pozycja zamówienia nie ma identyfikatora oferty.");
            }
            String description = resolveItemDescription(lineItem.offer());
            if (description == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pozycja zamówienia nie ma nazwy oferty.");
            }

            lines.add(new CreateInvoiceLineRequest(
                    itemCode,
                    description,
                    resolveItemType(),
                    BigDecimal.valueOf(qty),
                    unitNet,
                    tax.id(),
                    resolveItemUomName(context)));
        }

        BuyerBilling billing = billingMapper.toBuyerBilling(form);
        String headerComment = resolveHeaderComment(form.id(), billing.login());
        String footerComment = resolveFooterComment(billing.vatRegNo(), form.id());
        if (headerComment == null || footerComment == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Brak danych Allegro do komentarzy faktury.");
        }

        List<CreateInvoiceTaxAmountRequest> taxAmounts = vatByTaxId.entrySet().stream()
                .map(entry -> new CreateInvoiceTaxAmountRequest(entry.getKey(), entry.getValue()))
                .toList();

        return new CreateInvoiceRequest(
                context.customerId(),
                context.invoiceNo(),
                context.docDate(),
                context.docDate().plusDays(14),
                currency,
                headerComment,
                footerComment,
                totalNet,
                lines,
                taxAmounts);
    }

    public Instant earliestBoughtAt(List<AllegroLineItem> lineItems) {
        return lineItems.stream()
                .map(AllegroLineItem::boughtAt)
                .filter(Objects::nonNull)
                .min(Instant::compareTo)
                .orElse(null);
    }

    /** InvoiceRow[].Item.Code */
    public String resolveItemCode(AllegroOfferReference offer) {
        return AllegroInvoiceMappingSupport.resolveItemCode(offer);
    }

    /** InvoiceRow[].Item.Description */
    public String resolveItemDescription(AllegroOfferReference offer) {
        return AllegroInvoiceMappingSupport.resolveDescription(offer);
    }

    /** InvoiceRow[].Item.Type */
    public int resolveItemType() {
        return AllegroInvoiceMappingSupport.ITEM_TYPE_STOCK;
    }

    /** InvoiceRow[].Item.UOMName */
    public String resolveItemUomName(AllegroInvoiceMappingContext context) {
        return context.uomName();
    }

    /** InvoiceRow[].Quantity */
    public int resolveQuantity(AllegroLineItem lineItem) {
        return lineItem.quantity() == null ? 1 : lineItem.quantity();
    }

    /** InvoiceRow[].Price (netto) */
    public BigDecimal resolveUnitNet(BigDecimal unitGross, BigDecimal vatRate) {
        return AllegroInvoiceMappingSupport.toNet(unitGross, vatRate);
    }

    public String resolveHeaderComment(String orderId, String buyerLogin) {
        return AllegroInvoiceMappingSupport.resolveHeaderComment(orderId, buyerLogin);
    }

    public String resolveFooterComment(String vatRegNo, String orderId) {
        return AllegroInvoiceMappingSupport.resolveFooterComment(vatRegNo, orderId);
    }

    public MeritTaxDto resolveTax(AllegroLineItem lineItem, List<MeritTaxDto> taxes) {
        BigDecimal percent = allegroTaxPercent(lineItem.tax());
        if (percent == null) {
            return taxesService.requireFallbackTax(taxes);
        }
        return taxesService.resolveByPercent(percent, taxes);
    }

    private static BigDecimal allegroTaxPercent(AllegroLineItemTax tax) {
        if (tax == null || tax.rate() == null || tax.rate().isBlank()) {
            return null;
        }
        return new BigDecimal(tax.rate().trim());
    }

    private static BigDecimal parseAmount(String amount) {
        if (amount == null || amount.isBlank()) {
            return null;
        }
        return new BigDecimal(amount);
    }
}
