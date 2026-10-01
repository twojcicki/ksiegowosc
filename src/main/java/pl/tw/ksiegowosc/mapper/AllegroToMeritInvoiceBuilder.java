package pl.tw.ksiegowosc.mapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
import pl.tw.ksiegowosc.dto.CreateInvoicePaymentRequest;
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

    private static final BigDecimal GROSS_MATCH_TOLERANCE = new BigDecimal("0.01");

    private final AllegroBillingMapper billingMapper;
    private final TaxesService taxesService;

    public AllegroToMeritInvoiceBuilder(AllegroBillingMapper billingMapper, TaxesService taxesService) {
        this.billingMapper = billingMapper;
        this.taxesService = taxesService;
    }

    public CreateInvoiceRequest toCreateInvoiceRequest(
            AllegroCheckoutForm form,
            AllegroInvoiceMappingContext context) {
        List<BuiltLine> builtLines = new ArrayList<>();
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
            builtLines.add(buildPricedLine(
                    resolveItemCodeRequired(lineItem.offer()),
                    resolveItemDescriptionRequired(lineItem.offer()),
                    resolveItemType(),
                    resolveQuantity(lineItem),
                    unitGross,
                    tax,
                    false));

            if (lineItem.selectedAdditionalServices() != null) {
                for (AllegroAdditionalService service : lineItem.selectedAdditionalServices()) {
                    if (service == null) {
                        continue;
                    }
                    currency = preferCurrency(currency, service.price());
                    BuiltLine serviceLine = buildServiceLine(
                            resolveAdditionalServiceCode(service),
                            resolveAdditionalServiceDescription(service),
                            resolveAdditionalServiceQuantity(service),
                            service.price(),
                            context.taxes(),
                            false);
                    if (serviceLine != null) {
                        builtLines.add(serviceLine);
                    }
                }
            }
        }

        AllegroDelivery delivery = form.delivery();
        if (delivery != null) {
            currency = preferCurrency(currency, delivery.cost());
            BuiltLine deliveryLine = buildServiceLine(
                    resolveDeliveryCode(delivery.method()),
                    resolveDeliveryDescription(delivery.method()),
                    1,
                    delivery.cost(),
                    context.taxes(),
                    true);
            if (deliveryLine != null) {
                builtLines.add(deliveryLine);
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
                BuiltLine surchargeLine = buildServiceLine(
                        resolveSurchargeCode(surcharge, index),
                        resolveSurchargeDescription(surcharge),
                        1,
                        surcharge.paidAmount(),
                        context.taxes(),
                        false);
                if (surchargeLine != null) {
                    builtLines.add(surchargeLine);
                }
            }
        }

        matchBuiltLinesToAllegroGross(form, builtLines);

        BigDecimal totalNetPrecise = BigDecimal.ZERO.setScale(AllegroInvoiceMappingSupport.UNIT_NET_SCALE, RoundingMode.HALF_UP);
        BigDecimal totalGross = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        Map<String, BigDecimal> vatByTaxId = new LinkedHashMap<>();
        List<CreateInvoiceLineRequest> lines = new ArrayList<>(builtLines.size());
        String lastTaxId = null;
        for (BuiltLine builtLine : builtLines) {
            totalNetPrecise = totalNetPrecise.add(builtLine.lineNet());
            totalGross = totalGross.add(builtLine.lineGross());
            vatByTaxId.merge(builtLine.tax().id(), builtLine.lineVat(), BigDecimal::add);
            lastTaxId = builtLine.tax().id();
            lines.add(builtLine.toRequest(resolveItemUomName(context)));
        }
        BigDecimal totalNet = totalNetPrecise.setScale(2, RoundingMode.HALF_UP);
        if (lastTaxId != null) {
            BigDecimal expectedTax = totalGross.subtract(totalNet);
            BigDecimal taxSum = vatByTaxId.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal taxDelta = expectedTax.subtract(taxSum);
            if (taxDelta.compareTo(BigDecimal.ZERO) != 0) {
                vatByTaxId.merge(lastTaxId, taxDelta, BigDecimal::add);
            }
        }

        BuyerBilling billing = billingMapper.toBuyerBilling(form);
        String headerComment = resolveHeaderComment(
                context.accountName(), context.sellerLogin(), form.id(), billing.login());
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
                null,
                context.transactionDate(),
                currency,
                headerComment,
                null,
                totalNet,
                lines,
                taxAmounts,
                resolvePayment(form, context));
    }

    CreateInvoicePaymentRequest resolvePayment(AllegroCheckoutForm form, AllegroInvoiceMappingContext context) {
        if (form == null || form.payment() == null) {
            return null;
        }
        String method = context == null ? null : context.paymentMethod();
        if (method == null || method.isBlank()) {
            return null;
        }
        AllegroPrice paid = form.payment().paidAmount();
        if (paid == null || paid.amount() == null || paid.amount().isBlank()) {
            return null;
        }
        BigDecimal paidAmount = parseAmount(paid.amount());
        if (paidAmount == null || paidAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        String paymDate = formatPaymentDate(form.payment().finishedAt());
        if (paymDate == null) {
            return null;
        }
        return new CreateInvoicePaymentRequest(method.trim(), paidAmount, paymDate);
    }

    private static String formatPaymentDate(Instant finishedAt) {
        if (finishedAt == null) {
            return null;
        }
        return DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                .withZone(ZoneId.of("Europe/Warsaw"))
                .format(finishedAt);
    }

    private BuiltLine buildServiceLine(
            String itemCode,
            String description,
            int quantity,
            AllegroPrice price,
            List<MeritTaxDto> taxes,
            boolean preferredForGrossAdjust) {
        BigDecimal unitGross = parseAmount(price == null ? null : price.amount());
        if (unitGross == null || unitGross.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        MeritTaxDto tax = taxesService.requireFallbackTax(taxes);
        return buildPricedLine(
                itemCode,
                description,
                AllegroInvoiceMappingSupport.ITEM_TYPE_SERVICE,
                quantity,
                unitGross,
                tax,
                preferredForGrossAdjust);
    }

    private BuiltLine buildPricedLine(
            String itemCode,
            String description,
            int itemType,
            int quantity,
            BigDecimal unitGross,
            MeritTaxDto tax,
            boolean preferredForGrossAdjust) {
        return BuiltLine.fromUnitGross(
                itemCode, description, itemType, quantity, unitGross, tax, preferredForGrossAdjust);
    }

    private void matchBuiltLinesToAllegroGross(AllegroCheckoutForm form, List<BuiltLine> builtLines) {
        BigDecimal targetGross = resolveTargetGross(form);
        if (targetGross == null) {
            return;
        }
        BigDecimal invoiceGross = sumLineGross(builtLines);
        BigDecimal delta = targetGross.subtract(invoiceGross);
        if (delta.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        if (delta.abs().compareTo(GROSS_MATCH_TOLERANCE) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    grossMismatchMessage(invoiceGross, targetGross));
        }
        if (builtLines.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    grossMismatchMessage(invoiceGross, targetGross));
        }
        int adjustIndex = indexForGrossAdjust(builtLines);
        BuiltLine current = builtLines.get(adjustIndex);
        builtLines.set(adjustIndex, current.withAdjustedLineGross(current.lineGross().add(delta)));
        BigDecimal matchedGross = sumLineGross(builtLines);
        if (matchedGross.compareTo(targetGross) != 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    grossMismatchMessage(matchedGross, targetGross));
        }
    }

    static String grossMismatchMessage(BigDecimal invoiceGross, BigDecimal targetGross) {
        return "Kwota pozycji ("
                + invoiceGross.toPlainString()
                + ") różni się od kwoty Allegro ("
                + targetGross.toPlainString()
                + ") o więcej niż 0,01 PLN.";
    }

    public static boolean isGrossMismatchMessage(String message) {
        return message != null && message.contains("różni się od kwoty Allegro");
    }

    private static int indexForGrossAdjust(List<BuiltLine> builtLines) {
        for (int i = builtLines.size() - 1; i >= 0; i--) {
            if (builtLines.get(i).preferredForGrossAdjust()) {
                return i;
            }
        }
        return builtLines.size() - 1;
    }

    private static BigDecimal sumLineGross(List<BuiltLine> builtLines) {
        BigDecimal total = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (BuiltLine line : builtLines) {
            total = total.add(line.lineGross());
        }
        return total;
    }

    /**
     * Cel brutto faktury: payment.paidAmount, inaczej summary.totalToPay.
     */
    static BigDecimal resolveTargetGross(AllegroCheckoutForm form) {
        if (form.payment() != null && form.payment().paidAmount() != null) {
            BigDecimal paid = parseAmount(form.payment().paidAmount().amount());
            if (paid != null && paid.compareTo(BigDecimal.ZERO) > 0) {
                return paid.setScale(2, RoundingMode.HALF_UP);
            }
        }
        if (form.summary() != null && form.summary().totalToPay() != null) {
            BigDecimal total = parseAmount(form.summary().totalToPay().amount());
            if (total != null) {
                return total.setScale(2, RoundingMode.HALF_UP);
            }
        }
        return null;
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
        return AllegroInvoiceMappingSupport.toNet(
                unitGross, vatRate, AllegroInvoiceMappingSupport.UNIT_NET_SCALE);
    }

    public BigDecimal resolveUnitNetFromLine(BigDecimal lineNet, int quantity) {
        return AllegroInvoiceMappingSupport.unitNetFromLineNet(lineNet, quantity);
    }

    public String resolveDeliveryCode(AllegroDeliveryMethod method) {
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

    public String resolveHeaderComment(
            String accountName, String sellerLogin, String orderId, String buyerLogin) {
        return AllegroInvoiceMappingSupport.resolveHeaderComment(
                accountName, sellerLogin, orderId, buyerLogin);
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

    private record BuiltLine(
            String itemCode,
            String description,
            int itemType,
            int quantity,
            BigDecimal unitGross,
            BigDecimal lineGross,
            BigDecimal lineNet,
            BigDecimal lineVat,
            BigDecimal unitNet,
            MeritTaxDto tax,
            boolean preferredForGrossAdjust) {

        static BuiltLine fromUnitGross(
                String itemCode,
                String description,
                int itemType,
                int quantity,
                BigDecimal unitGross,
                MeritTaxDto tax,
                boolean preferredForGrossAdjust) {
            BigDecimal vatRate = AllegroInvoiceMappingSupport.vatRateFromPercent(tax.taxPct());
            BigDecimal unitNet = AllegroInvoiceMappingSupport.toNet(
                    unitGross, vatRate, AllegroInvoiceMappingSupport.UNIT_NET_SCALE);
            BigDecimal lineNet = AllegroInvoiceMappingSupport.lineNetFromUnitNet(unitNet, quantity);
            BigDecimal lineGross =
                    unitGross.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineVat = lineGross.subtract(lineNet.setScale(2, RoundingMode.HALF_UP));
            return new BuiltLine(
                    itemCode,
                    description,
                    itemType,
                    quantity,
                    unitGross,
                    lineGross,
                    lineNet,
                    lineVat,
                    unitNet,
                    tax,
                    preferredForGrossAdjust);
        }

        BuiltLine withAdjustedLineGross(BigDecimal adjustedLineGross) {
            BigDecimal scaledLineGross = adjustedLineGross.setScale(2, RoundingMode.HALF_UP);
            BigDecimal adjustedUnitGross = scaledLineGross.divide(
                    BigDecimal.valueOf(quantity),
                    AllegroInvoiceMappingSupport.UNIT_NET_SCALE,
                    RoundingMode.HALF_UP);
            return fromUnitGross(
                    itemCode,
                    description,
                    itemType,
                    quantity,
                    adjustedUnitGross,
                    tax,
                    preferredForGrossAdjust);
        }

        CreateInvoiceLineRequest toRequest(String uomName) {
            return new CreateInvoiceLineRequest(
                    itemCode,
                    description,
                    itemType,
                    BigDecimal.valueOf(quantity),
                    unitNet,
                    tax.id(),
                    uomName);
        }
    }
}
