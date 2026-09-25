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
import pl.tw.ksiegowosc.dto.allegro.AllegroAdditionalService;
import pl.tw.ksiegowosc.dto.allegro.AllegroCheckoutForm;
import pl.tw.ksiegowosc.dto.allegro.AllegroDelivery;
import pl.tw.ksiegowosc.dto.allegro.AllegroDeliveryMethod;
import pl.tw.ksiegowosc.dto.allegro.AllegroLineItem;
import pl.tw.ksiegowosc.dto.allegro.AllegroLineItemTax;
import pl.tw.ksiegowosc.dto.allegro.AllegroOfferReference;
import pl.tw.ksiegowosc.dto.allegro.AllegroPrice;
import pl.tw.ksiegowosc.dto.allegro.AllegroSurcharge;
import pl.tw.ksiegowosc.service.TaxesService;

/**
 * Jedno miejsce budujące CreateInvoiceRequest z Allegro — pola zgodne z {@link AllegroMeritInvoiceMappings#RULES}.
 */
@Component
public class AllegroToMeritInvoiceBuilder {

    private static final BigDecimal TOTAL_TOLERANCE = new BigDecimal("0.01");

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
        BigDecimal totalGross = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        Map<String, BigDecimal> vatByTaxId = new LinkedHashMap<>();
        String currency = "PLN";

        List<AllegroLineItem> lineItems = form.lineItems() == null ? List.of() : form.lineItems();
        for (AllegroLineItem lineItem : lineItems) {
            AllegroPrice price = lineItem.price();
            BigDecimal unitGross = parseAmount(price == null ? null : price.amount());
            if (unitGross == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pozycja zamówienia nie ma ceny.");
            }
            currency = preferCurrency(currency, price);

            MeritTaxDto tax = resolveTax(lineItem, context.taxes());
            LineAmounts amounts = addPricedLine(
                    lines,
                    vatByTaxId,
                    context,
                    resolveItemCodeRequired(lineItem.offer()),
                    resolveItemDescriptionRequired(lineItem.offer()),
                    resolveItemType(),
                    resolveQuantity(lineItem),
                    unitGross,
                    tax);
            totalNet = totalNet.add(amounts.lineNet());
            totalGross = totalGross.add(amounts.lineGross());

            if (lineItem.selectedAdditionalServices() != null) {
                for (AllegroAdditionalService service : lineItem.selectedAdditionalServices()) {
                    if (service == null) {
                        continue;
                    }
                    currency = preferCurrency(currency, service.price());
                    LineAmounts serviceAmounts = addServiceLine(
                            lines,
                            vatByTaxId,
                            context,
                            resolveAdditionalServiceCode(service),
                            resolveAdditionalServiceDescription(service),
                            resolveAdditionalServiceQuantity(service),
                            service.price());
                    if (serviceAmounts != null) {
                        totalNet = totalNet.add(serviceAmounts.lineNet());
                        totalGross = totalGross.add(serviceAmounts.lineGross());
                    }
                }
            }
        }

        AllegroDelivery delivery = form.delivery();
        if (delivery != null) {
            currency = preferCurrency(currency, delivery.cost());
            LineAmounts deliveryAmounts = addServiceLine(
                    lines,
                    vatByTaxId,
                    context,
                    resolveDeliveryCode(delivery.method()),
                    resolveDeliveryDescription(delivery.method()),
                    1,
                    delivery.cost());
            if (deliveryAmounts != null) {
                totalNet = totalNet.add(deliveryAmounts.lineNet());
                totalGross = totalGross.add(deliveryAmounts.lineGross());
            }
        }

        if (form.surcharges() != null) {
            int index = 0;
            for (AllegroSurcharge surcharge : form.surcharges()) {
                index++;
                if (surcharge == null) {
                    continue;
                }
                currency = preferCurrency(currency, surcharge.paidAmount());
                LineAmounts surchargeAmounts = addServiceLine(
                        lines,
                        vatByTaxId,
                        context,
                        resolveSurchargeCode(surcharge, index),
                        resolveSurchargeDescription(surcharge),
                        1,
                        surcharge.paidAmount());
                if (surchargeAmounts != null) {
                    totalNet = totalNet.add(surchargeAmounts.lineNet());
                    totalGross = totalGross.add(surchargeAmounts.lineGross());
                }
            }
        }

        validateAgainstTotalToPay(form, totalGross);

        BuyerBilling billing = billingMapper.toBuyerBilling(form);
        String headerComment = resolveHeaderComment(form.id(), billing.login());
        if (headerComment == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Brak danych Allegro do komentarza faktury.");
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
                null,
                totalNet,
                lines,
                taxAmounts);
    }

    private LineAmounts addServiceLine(
            List<CreateInvoiceLineRequest> lines,
            Map<String, BigDecimal> vatByTaxId,
            AllegroInvoiceMappingContext context,
            String itemCode,
            String description,
            int quantity,
            AllegroPrice price) {
        BigDecimal unitGross = parseAmount(price == null ? null : price.amount());
        if (unitGross == null || unitGross.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        MeritTaxDto tax = taxesService.requireFallbackTax(context.taxes());
        return addPricedLine(
                lines,
                vatByTaxId,
                context,
                itemCode,
                description,
                AllegroInvoiceMappingSupport.ITEM_TYPE_SERVICE,
                quantity,
                unitGross,
                tax);
    }

    private LineAmounts addPricedLine(
            List<CreateInvoiceLineRequest> lines,
            Map<String, BigDecimal> vatByTaxId,
            AllegroInvoiceMappingContext context,
            String itemCode,
            String description,
            int itemType,
            int quantity,
            BigDecimal unitGross,
            MeritTaxDto tax) {
        BigDecimal vatRate = AllegroInvoiceMappingSupport.vatRateFromPercent(tax.taxPct());
        BigDecimal unitNet = resolveUnitNet(unitGross, vatRate);
        BigDecimal lineNet = unitNet.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal lineGross = unitGross.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal lineVat = lineGross.subtract(lineNet);
        vatByTaxId.merge(tax.id(), lineVat, BigDecimal::add);
        lines.add(new CreateInvoiceLineRequest(
                itemCode,
                description,
                itemType,
                BigDecimal.valueOf(quantity),
                unitNet,
                tax.id(),
                resolveItemUomName(context)));
        return new LineAmounts(lineNet, lineGross);
    }

    private static void validateAgainstTotalToPay(AllegroCheckoutForm form, BigDecimal invoiceGross) {
        if (form.summary() == null || form.summary().totalToPay() == null) {
            return;
        }
        BigDecimal expected = parseAmount(form.summary().totalToPay().amount());
        if (expected == null) {
            return;
        }
        BigDecimal delta = invoiceGross.subtract(expected).abs();
        if (delta.compareTo(TOTAL_TOLERANCE) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Kwota faktury (" + invoiceGross.toPlainString()
                            + ") nie zgadza się z Allegro summary.totalToPay ("
                            + expected.toPlainString() + ").");
        }
    }

    public Instant earliestBoughtAt(List<AllegroLineItem> lineItems) {
        return lineItems.stream()
                .map(AllegroLineItem::boughtAt)
                .filter(Objects::nonNull)
                .min(Instant::compareTo)
                .orElse(null);
    }

    public String resolveItemCode(AllegroOfferReference offer) {
        return AllegroInvoiceMappingSupport.resolveItemCode(offer);
    }

    public String resolveItemDescription(AllegroOfferReference offer) {
        return AllegroInvoiceMappingSupport.resolveDescription(offer);
    }

    public int resolveItemType() {
        return AllegroInvoiceMappingSupport.ITEM_TYPE_STOCK;
    }

    public String resolveItemUomName(AllegroInvoiceMappingContext context) {
        return context.uomName();
    }

    public int resolveQuantity(AllegroLineItem lineItem) {
        return lineItem.quantity() == null ? 1 : lineItem.quantity();
    }

    public BigDecimal resolveUnitNet(BigDecimal unitGross, BigDecimal vatRate) {
        return AllegroInvoiceMappingSupport.toNet(unitGross, vatRate);
    }

    public String resolveDeliveryCode(AllegroDeliveryMethod method) {
        if (method != null && method.id() != null && !method.id().isBlank()) {
            return AllegroInvoiceMappingSupport.truncateItemCode(method.id());
        }
        return AllegroInvoiceMappingSupport.FALLBACK_DELIVERY_CODE;
    }

    public String resolveDeliveryDescription(AllegroDeliveryMethod method) {
        if (method != null && method.name() != null && !method.name().isBlank()) {
            return method.name().trim();
        }
        return "Dostawa";
    }

    public String resolveSurchargeCode(AllegroSurcharge surcharge, int index) {
        if (surcharge != null && surcharge.id() != null && !surcharge.id().isBlank()) {
            return AllegroInvoiceMappingSupport.truncateItemCode(surcharge.id());
        }
        return AllegroInvoiceMappingSupport.FALLBACK_SURCHARGE_CODE + index;
    }

    public String resolveSurchargeDescription(AllegroSurcharge surcharge) {
        if (surcharge != null && surcharge.type() != null && !surcharge.type().isBlank()) {
            return "Dopłata " + surcharge.type().trim();
        }
        return "Dopłata";
    }

    public String resolveAdditionalServiceCode(AllegroAdditionalService service) {
        if (service != null && service.definitionId() != null && !service.definitionId().isBlank()) {
            return AllegroInvoiceMappingSupport.truncateItemCode(service.definitionId());
        }
        return "USLUGA";
    }

    public String resolveAdditionalServiceDescription(AllegroAdditionalService service) {
        if (service != null && service.name() != null && !service.name().isBlank()) {
            return service.name().trim();
        }
        return "Usługa dodatkowa";
    }

    public int resolveAdditionalServiceQuantity(AllegroAdditionalService service) {
        return service == null || service.quantity() == null || service.quantity() < 1 ? 1 : service.quantity();
    }

    public String resolveHeaderComment(String orderId, String buyerLogin) {
        return AllegroInvoiceMappingSupport.resolveHeaderComment(orderId, buyerLogin);
    }

    public MeritTaxDto resolveTax(AllegroLineItem lineItem, List<MeritTaxDto> taxes) {
        BigDecimal percent = allegroTaxPercent(lineItem.tax());
        if (percent == null) {
            return taxesService.requireFallbackTax(taxes);
        }
        return taxesService.resolveByPercent(percent, taxes);
    }

    private String resolveItemCodeRequired(AllegroOfferReference offer) {
        String itemCode = resolveItemCode(offer);
        if (itemCode == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pozycja zamówienia nie ma identyfikatora oferty.");
        }
        return itemCode;
    }

    private String resolveItemDescriptionRequired(AllegroOfferReference offer) {
        String description = resolveItemDescription(offer);
        if (description == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pozycja zamówienia nie ma nazwy oferty.");
        }
        return description;
    }

    private static String preferCurrency(String current, AllegroPrice price) {
        if (price != null && price.currency() != null && !price.currency().isBlank()) {
            return price.currency();
        }
        return current;
    }

    private static BigDecimal allegroTaxPercent(AllegroLineItemTax tax) {
        if (tax == null || tax.rate() == null || tax.rate().isBlank()) {
            return null;
        }
        return new BigDecimal(tax.rate().trim());
    }

    static BigDecimal parseAmount(String amount) {
        if (amount == null || amount.isBlank()) {
            return null;
        }
        return new BigDecimal(amount);
    }

    /**
     * Kwota brutto płacona przez kupującego: summary.totalToPay albo suma linii + dostawa + dopłaty + usługi.
     */
    public static BigDecimal resolveBuyerTotalGross(AllegroCheckoutForm form) {
        if (form.summary() != null && form.summary().totalToPay() != null) {
            BigDecimal total = parseAmount(form.summary().totalToPay().amount());
            if (total != null) {
                return total.setScale(2, RoundingMode.HALF_UP);
            }
        }
        BigDecimal total = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        List<AllegroLineItem> lineItems = form.lineItems() == null ? List.of() : form.lineItems();
        for (AllegroLineItem lineItem : lineItems) {
            AllegroPrice price = lineItem.price();
            BigDecimal unit = parseAmount(price == null ? null : price.amount());
            int qty = lineItem.quantity() == null ? 0 : lineItem.quantity();
            if (unit != null) {
                total = total.add(unit.multiply(BigDecimal.valueOf(qty)));
            }
            if (lineItem.selectedAdditionalServices() != null) {
                for (AllegroAdditionalService service : lineItem.selectedAdditionalServices()) {
                    if (service == null) {
                        continue;
                    }
                    BigDecimal serviceUnit = parseAmount(service.price() == null ? null : service.price().amount());
                    int serviceQty = service.quantity() == null || service.quantity() < 1 ? 1 : service.quantity();
                    if (serviceUnit != null) {
                        total = total.add(serviceUnit.multiply(BigDecimal.valueOf(serviceQty)));
                    }
                }
            }
        }
        if (form.delivery() != null && form.delivery().cost() != null) {
            BigDecimal delivery = parseAmount(form.delivery().cost().amount());
            if (delivery != null) {
                total = total.add(delivery);
            }
        }
        if (form.surcharges() != null) {
            for (AllegroSurcharge surcharge : form.surcharges()) {
                if (surcharge == null || surcharge.paidAmount() == null) {
                    continue;
                }
                BigDecimal paid = parseAmount(surcharge.paidAmount().amount());
                if (paid != null) {
                    total = total.add(paid);
                }
            }
        }
        return total;
    }

    private record LineAmounts(BigDecimal lineNet, BigDecimal lineGross) {
    }
}
