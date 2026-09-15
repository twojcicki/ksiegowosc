package pl.tw.ksiegowosc.mapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import pl.tw.ksiegowosc.dto.AllegroSoldItemDto;
import pl.tw.ksiegowosc.dto.allegro.AllegroBuyer;
import pl.tw.ksiegowosc.dto.allegro.AllegroCheckoutForm;
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

        BigDecimal totalGross = AllegroToMeritInvoiceBuilder.resolveBuyerTotalGross(form);
        String currency = resolveCurrency(form);

        return new AllegroSoldItemDto(
                accountId,
                accountName,
                form.id(),
                summarizeName(lineItems),
                lineItems.size(),
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
                order.totalGross(),
                order.currency(),
                order.boughtAt(),
                order.buyerLogin(),
                order.orderStatus(),
                order.fulfillmentStatus(),
                invoiceNo);
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
