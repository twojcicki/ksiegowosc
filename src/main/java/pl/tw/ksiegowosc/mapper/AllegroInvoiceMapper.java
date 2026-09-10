package pl.tw.ksiegowosc.mapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import pl.tw.ksiegowosc.dto.BuyerBilling;
import pl.tw.ksiegowosc.dto.CreateInvoiceLineRequest;
import pl.tw.ksiegowosc.dto.CreateInvoiceRequest;
import pl.tw.ksiegowosc.dto.CreateInvoiceTaxAmountRequest;
import pl.tw.ksiegowosc.dto.MeritCreateCustomerRequest;
import pl.tw.ksiegowosc.dto.MeritTaxDto;
import pl.tw.ksiegowosc.dto.allegro.AllegroCheckoutForm;
import pl.tw.ksiegowosc.dto.allegro.AllegroLineItem;
import pl.tw.ksiegowosc.dto.allegro.AllegroLineItemTax;
import pl.tw.ksiegowosc.dto.allegro.AllegroPrice;
import pl.tw.ksiegowosc.service.TaxesService;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public abstract class AllegroInvoiceMapper {

    @Autowired
    protected AllegroBillingMapper billingMapper;

    @Autowired
    protected TaxesService taxesService;

    @Mapping(target = "currencyCode", constant = "PLN")
    @Mapping(target = "salesInvLang", constant = "PL")
    @Mapping(target = "vatRegNo", source = "vatRegNo", qualifiedByName = "blankToNull")
    @Mapping(target = "address", source = "address", qualifiedByName = "blankToNull")
    @Mapping(target = "city", source = "city", qualifiedByName = "blankToNull")
    @Mapping(target = "postalCode", source = "postalCode", qualifiedByName = "blankToNull")
    @Mapping(target = "email", source = "email", qualifiedByName = "blankToNull")
    public abstract MeritCreateCustomerRequest toCustomerRequest(BuyerBilling billing);

    @Named("blankToNull")
    protected String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
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
            int qty = lineItem.quantity() == null ? 1 : lineItem.quantity();
            BigDecimal unitNet = toNet(unitGross, vatRate);
            BigDecimal lineNet = unitNet.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineGross = unitGross.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineVat = lineGross.subtract(lineNet);

            totalNet = totalNet.add(lineNet);
            vatByTaxId.merge(tax.id(), lineVat, BigDecimal::add);

            String itemCode = AllegroInvoiceMappingSupport.resolveItemCode(lineItem.offer());
            if (itemCode == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pozycja zamówienia nie ma identyfikatora oferty.");
            }
            String description = AllegroInvoiceMappingSupport.resolveDescription(lineItem.offer());
            if (description == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pozycja zamówienia nie ma nazwy oferty.");
            }

            lines.add(new CreateInvoiceLineRequest(
                    itemCode,
                    description,
                    AllegroInvoiceMappingSupport.ITEM_TYPE_STOCK,
                    BigDecimal.valueOf(qty),
                    unitNet,
                    tax.id(),
                    AllegroInvoiceMappingSupport.DEFAULT_UOM_NAME));
        }

        BuyerBilling billing = billingMapper.toBuyerBilling(form);
        String headerComment = AllegroInvoiceMappingSupport.resolveHeaderComment(form.id(), billing.login());
        String footerComment = AllegroInvoiceMappingSupport.resolveFooterComment(billing.vatRegNo(), form.id());
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

    protected MeritTaxDto resolveTax(AllegroLineItem lineItem, List<MeritTaxDto> taxes) {
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

    public String buildInvoiceNo(String orderId, LocalDate docDate) {
        return AllegroInvoiceMappingSupport.buildInvoiceNo(orderId, docDate);
    }

    public BigDecimal toNet(BigDecimal gross, BigDecimal vatRate) {
        return AllegroInvoiceMappingSupport.toNet(gross, vatRate);
    }

    public Instant earliestBoughtAt(List<AllegroLineItem> lineItems) {
        return lineItems.stream()
                .map(AllegroLineItem::boughtAt)
                .filter(Objects::nonNull)
                .min(Instant::compareTo)
                .orElse(null);
    }

    private static BigDecimal parseAmount(String amount) {
        if (amount == null || amount.isBlank()) {
            return null;
        }
        return new BigDecimal(amount);
    }
}
