package pl.tw.ksiegowosc.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import pl.tw.ksiegowosc.client.AllegroApiClient;
import pl.tw.ksiegowosc.dto.AllegroSoldItemDto;
import pl.tw.ksiegowosc.dto.allegro.AllegroBuyer;
import pl.tw.ksiegowosc.dto.allegro.AllegroCheckoutForm;
import pl.tw.ksiegowosc.dto.allegro.AllegroCheckoutFormsResponse;
import pl.tw.ksiegowosc.dto.allegro.AllegroFulfillment;
import pl.tw.ksiegowosc.dto.allegro.AllegroLineItem;
import pl.tw.ksiegowosc.dto.allegro.AllegroPrice;
import pl.tw.ksiegowosc.entity.AllegroSoldInvoice;
import pl.tw.ksiegowosc.repository.AllegroSoldInvoiceRepository;

@Service
public class AllegroOrdersService {

    private static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");

    private final AllegroApiClient allegroApiClient;
    private final AllegroAuthService authService;
    private final AllegroSoldInvoiceRepository soldInvoiceRepository;

    public AllegroOrdersService(
            AllegroApiClient allegroApiClient,
            AllegroAuthService authService,
            AllegroSoldInvoiceRepository soldInvoiceRepository) {
        this.allegroApiClient = allegroApiClient;
        this.authService = authService;
        this.soldInvoiceRepository = soldInvoiceRepository;
    }

    public List<AllegroSoldItemDto> getSoldItems(
            LocalDate from,
            LocalDate to,
            int offset,
            int limit) {
        validateDateRange(from, to);
        authService.getValidAccessToken();

        Instant boughtAtFrom = from.atStartOfDay(ZONE).toInstant();
        Instant boughtAtTo = to.plusDays(1).atStartOfDay(ZONE).toInstant().minusMillis(1);

        AllegroCheckoutFormsResponse response = allegroApiClient.getCheckoutForms(
                offset, limit, boughtAtFrom, boughtAtTo);
        if (response == null || response.checkoutForms() == null) {
            return List.of();
        }

        List<AllegroSoldItemDto> orders = new ArrayList<>();
        for (AllegroCheckoutForm form : response.checkoutForms()) {
            orders.add(toDto(form, null));
        }

        Map<String, String> invoiceNos = loadInvoiceNos(orders.stream()
                .map(AllegroSoldItemDto::orderId)
                .filter(Objects::nonNull)
                .toList());

        if (invoiceNos.isEmpty()) {
            return Collections.unmodifiableList(orders);
        }

        List<AllegroSoldItemDto> withInvoices = new ArrayList<>(orders.size());
        for (AllegroSoldItemDto order : orders) {
            withInvoices.add(toDtoWithInvoice(order, invoiceNos.get(order.orderId())));
        }
        return Collections.unmodifiableList(withInvoices);
    }

    private Map<String, String> loadInvoiceNos(List<String> orderIds) {
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        return soldInvoiceRepository.findByOrderIdIn(orderIds).stream()
                .collect(Collectors.toMap(AllegroSoldInvoice::getOrderId, AllegroSoldInvoice::getInvoiceNo));
    }

    static AllegroSoldItemDto toDto(AllegroCheckoutForm form, String invoiceNo) {
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

        String name = summarizeName(lineItems);

        return new AllegroSoldItemDto(
                form.id(),
                name,
                lineItems.size(),
                totalGross,
                currency,
                boughtAt,
                buyer == null ? null : buyer.login(),
                form.status(),
                fulfillment == null ? null : fulfillment.status(),
                invoiceNo);
    }

    private static AllegroSoldItemDto toDtoWithInvoice(AllegroSoldItemDto order, String invoiceNo) {
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
        String first = lineItems.getFirst().name() == null ? "" : lineItems.getFirst().name();
        if (lineItems.size() == 1) {
            return first;
        }
        return first + " (+" + (lineItems.size() - 1) + ")";
    }

    private static void validateDateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Podaj zakres dat (from, to).");
        }
        if (from.isAfter(to)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Data początkowa nie może być późniejsza niż końcowa.");
        }
    }

    private static BigDecimal parseAmount(String amount) {
        if (amount == null || amount.isBlank()) {
            return null;
        }
        return new BigDecimal(amount);
    }
}
