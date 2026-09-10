package pl.tw.ksiegowosc.mapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    default AllegroSoldItemDto toDto(AllegroCheckoutForm form, String invoiceNo) {
        List<AllegroLineItem> lineItems = form.lineItems() == null ? List.of() : form.lineItems();
        AllegroBuyer buyer = form.buyer();
        AllegroFulfillment fulfillment = form.fulfillment();

        Instant boughtAt = lineItems.stream()
                .map(AllegroLineItem::boughtAt)
                .filter(Objects::nonNull)
                .min(Instant::compareTo)
                .orElse(null);

        BigDecimal totalGross = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        String currency = null;
        for (AllegroLineItem lineItem : lineItems) {
            AllegroPrice price = lineItem.price();
            BigDecimal unit = parseAmount(price == null ? null : price.amount());
            int qty = lineItem.quantity() == null ? 0 : lineItem.quantity();
            if (unit != null) {
                totalGross = totalGross.add(unit.multiply(BigDecimal.valueOf(qty)));
            }
            if (currency == null && price != null && price.currency() != null && !price.currency().isBlank()) {
                currency = price.currency();
            }
        }

        return new AllegroSoldItemDto(
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

    private static BigDecimal parseAmount(String amount) {
        if (amount == null || amount.isBlank()) {
            return null;
        }
        return new BigDecimal(amount);
    }
}
