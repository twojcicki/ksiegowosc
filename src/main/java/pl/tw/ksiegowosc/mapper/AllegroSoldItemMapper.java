package pl.tw.ksiegowosc.mapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import pl.tw.ksiegowosc.dto.AllegroSoldItemDto;
import pl.tw.ksiegowosc.dto.AllegroSoldLineDto;
import pl.tw.ksiegowosc.dto.allegro.AllegroBuyer;
import pl.tw.ksiegowosc.dto.allegro.AllegroCheckoutForm;
import pl.tw.ksiegowosc.dto.allegro.AllegroDelivery;
import pl.tw.ksiegowosc.dto.allegro.AllegroFulfillment;
import pl.tw.ksiegowosc.dto.allegro.AllegroLineItem;
import pl.tw.ksiegowosc.dto.allegro.AllegroOfferReference;
import pl.tw.ksiegowosc.dto.allegro.AllegroPrice;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface AllegroSoldItemMapper {

    default AllegroSoldItemDto toDto(
            AllegroCheckoutForm form,
            Long accountId,
            String accountName,
            String invoiceNo) {
        List<AllegroLineItem> lineItems = form.lineItems() == null ? List.of() : form.lineItems();
        AllegroBuyer buyer = form.buyer();
        AllegroFulfillment fulfillment = form.fulfillment();

        Instant boughtAt = lineItems.stream()
                .map(AllegroLineItem::boughtAt)
                .filter(Objects::nonNull)
                .min(Instant::compareTo)
                .orElse(null);

        List<AllegroSoldLineDto> lines = toLines(form);
        int quantityTotal = 0;
        for (AllegroLineItem lineItem : lineItems) {
            quantityTotal += lineItem.quantity() == null ? 0 : lineItem.quantity();
        }

        BigDecimal totalGross = AllegroToMeritInvoiceBuilder.resolveBuyerTotalGross(form);
        String currency = resolveCurrency(form);

        return new AllegroSoldItemDto(
                accountId,
                accountName,
                form.id(),
                summarizeName(lineItems),
                lineItems.size(),
                quantityTotal,
                lines,
                totalGross,
                currency,
                boughtAt,
                buyer == null ? null : buyer.login(),
                form.status(),
                fulfillment == null ? null : fulfillment.status(),
                invoiceNo);
    }

    default AllegroSoldItemDto withInvoiceNo(AllegroSoldItemDto order, String invoiceNo) {
        return new AllegroSoldItemDto(
                order.accountId(),
                order.accountName(),
                order.orderId(),
                order.name(),
                order.itemCount(),
                order.quantityTotal(),
                order.lines(),
                order.totalGross(),
                order.currency(),
                order.boughtAt(),
                order.buyerLogin(),
                order.orderStatus(),
                order.fulfillmentStatus(),
                invoiceNo);
    }

    private static List<AllegroSoldLineDto> toLines(AllegroCheckoutForm form) {
        List<AllegroLineItem> lineItems = form.lineItems() == null ? List.of() : form.lineItems();
        List<AllegroSoldLineDto> lines = new ArrayList<>(lineItems.size() + 1);
        for (AllegroLineItem lineItem : lineItems) {
            AllegroOfferReference offer = lineItem.offer();
            String name = offer == null || offer.name() == null ? "" : offer.name();
            AllegroPrice price = lineItem.price();
            BigDecimal unitPriceGross = null;
            if (price != null && price.amount() != null && !price.amount().isBlank()) {
                unitPriceGross = new BigDecimal(price.amount());
            }
            lines.add(new AllegroSoldLineDto(name, lineItem.quantity(), unitPriceGross));
        }
        AllegroSoldLineDto deliveryLine = toDeliveryLine(form.delivery());
        if (deliveryLine != null) {
            lines.add(deliveryLine);
        }
        return List.copyOf(lines);
    }

    private static AllegroSoldLineDto toDeliveryLine(AllegroDelivery delivery) {
        if (delivery == null || delivery.cost() == null) {
            return null;
        }
        AllegroPrice cost = delivery.cost();
        if (cost.amount() == null || cost.amount().isBlank()) {
            return null;
        }
        String name = "Dostawa";
        if (delivery.method() != null
                && delivery.method().name() != null
                && !delivery.method().name().isBlank()) {
            name = delivery.method().name().trim();
        }
        return new AllegroSoldLineDto(name, 1, new BigDecimal(cost.amount()));
    }

    private static String resolveCurrency(AllegroCheckoutForm form) {
        if (form.summary() != null
                && form.summary().totalToPay() != null
                && form.summary().totalToPay().currency() != null
                && !form.summary().totalToPay().currency().isBlank()) {
            return form.summary().totalToPay().currency();
        }
        if (form.delivery() != null
                && form.delivery().cost() != null
                && form.delivery().cost().currency() != null
                && !form.delivery().cost().currency().isBlank()) {
            return form.delivery().cost().currency();
        }
        List<AllegroLineItem> lineItems = form.lineItems() == null ? List.of() : form.lineItems();
        for (AllegroLineItem lineItem : lineItems) {
            AllegroPrice price = lineItem.price();
            if (price != null && price.currency() != null && !price.currency().isBlank()) {
                return price.currency();
            }
        }
        return null;
    }

    private static String summarizeName(List<AllegroLineItem> lineItems) {
        if (lineItems.isEmpty()) {
            return "";
        }
        AllegroOfferReference offer = lineItems.getFirst().offer();
        String first = offer == null || offer.name() == null ? "" : offer.name();
        if (lineItems.size() == 1) {
            return first;
        }
        return first + " (+" + (lineItems.size() - 1) + ")";
    }
}
