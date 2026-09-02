package pl.tw.ksiegowosc.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

@Service
public class AllegroOrdersService {

    private static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");

    private final AllegroApiClient allegroApiClient;
    private final AllegroAuthService authService;

    public AllegroOrdersService(AllegroApiClient allegroApiClient, AllegroAuthService authService) {
        this.allegroApiClient = allegroApiClient;
        this.authService = authService;
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

        List<AllegroSoldItemDto> items = new ArrayList<>();
        for (AllegroCheckoutForm form : response.checkoutForms()) {
            if (form.lineItems() == null) {
                continue;
            }
            for (AllegroLineItem lineItem : form.lineItems()) {
                items.add(toDto(form, lineItem));
            }
        }
        return Collections.unmodifiableList(items);
    }

    private static AllegroSoldItemDto toDto(AllegroCheckoutForm form, AllegroLineItem lineItem) {
        AllegroPrice price = lineItem.price();
        AllegroBuyer buyer = form.buyer();
        AllegroFulfillment fulfillment = form.fulfillment();

        return new AllegroSoldItemDto(
                form.id(),
                lineItem.offerId(),
                lineItem.name(),
                lineItem.quantity(),
                parseAmount(price == null ? null : price.amount()),
                price == null ? null : price.currency(),
                lineItem.boughtAt(),
                buyer == null ? null : buyer.login(),
                form.status(),
                fulfillment == null ? null : fulfillment.status());
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
