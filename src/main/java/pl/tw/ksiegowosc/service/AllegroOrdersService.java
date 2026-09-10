package pl.tw.ksiegowosc.service;

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
import pl.tw.ksiegowosc.dto.allegro.AllegroCheckoutForm;
import pl.tw.ksiegowosc.dto.allegro.AllegroCheckoutFormsResponse;
import pl.tw.ksiegowosc.entity.AllegroSoldInvoice;
import pl.tw.ksiegowosc.mapper.AllegroSoldItemMapper;
import pl.tw.ksiegowosc.repository.AllegroSoldInvoiceRepository;

@Service
public class AllegroOrdersService {

    private static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");

    private final AllegroApiClient allegroApiClient;
    private final AllegroAuthService authService;
    private final AllegroSoldInvoiceRepository soldInvoiceRepository;
    private final AllegroSoldItemMapper soldItemMapper;

    public AllegroOrdersService(
            AllegroApiClient allegroApiClient,
            AllegroAuthService authService,
            AllegroSoldInvoiceRepository soldInvoiceRepository,
            AllegroSoldItemMapper soldItemMapper) {
        this.allegroApiClient = allegroApiClient;
        this.authService = authService;
        this.soldInvoiceRepository = soldInvoiceRepository;
        this.soldItemMapper = soldItemMapper;
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
            orders.add(soldItemMapper.toDto(form, null));
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
            withInvoices.add(soldItemMapper.withInvoiceNo(order, invoiceNos.get(order.orderId())));
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
}
