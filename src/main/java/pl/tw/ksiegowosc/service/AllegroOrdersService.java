package pl.tw.ksiegowosc.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.server.ResponseStatusException;

import pl.tw.ksiegowosc.client.AllegroApiClient;
import pl.tw.ksiegowosc.dto.AllegroSoldItemDto;
import pl.tw.ksiegowosc.dto.AllegroSoldItemsResult;
import pl.tw.ksiegowosc.dto.allegro.AllegroCheckoutForm;
import pl.tw.ksiegowosc.dto.allegro.AllegroCheckoutFormsResponse;
import pl.tw.ksiegowosc.entity.AllegroAccount;
import pl.tw.ksiegowosc.entity.AllegroSoldInvoice;
import pl.tw.ksiegowosc.mapper.AllegroSoldItemMapper;
import pl.tw.ksiegowosc.repository.AllegroSoldInvoiceRepository;

@Service
public class AllegroOrdersService {

    private static final Logger log = LoggerFactory.getLogger(AllegroOrdersService.class);
    private static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");

    private final AllegroApiClient allegroApiClient;
    private final AllegroAuthService authService;
    private final AllegroAccountService accountService;
    private final AllegroSoldInvoiceRepository soldInvoiceRepository;
    private final AllegroSoldItemMapper soldItemMapper;

    public AllegroOrdersService(
            AllegroApiClient allegroApiClient,
            AllegroAuthService authService,
            AllegroAccountService accountService,
            AllegroSoldInvoiceRepository soldInvoiceRepository,
            AllegroSoldItemMapper soldItemMapper) {
        this.allegroApiClient = allegroApiClient;
        this.authService = authService;
        this.accountService = accountService;
        this.soldInvoiceRepository = soldInvoiceRepository;
        this.soldItemMapper = soldItemMapper;
    }

    public AllegroSoldItemsResult getSoldItems(
            LocalDate from,
            LocalDate to,
            int offset,
            int limit) {
        validateDateRange(from, to);
        Instant boughtAtFrom = from.atStartOfDay(ZONE).toInstant();
        Instant boughtAtTo = to.plusDays(1).atStartOfDay(ZONE).toInstant().minusMillis(1);
        return getSoldItems(boughtAtFrom, boughtAtTo, offset, limit, false);
    }

    /**
     * Sold items from {@code from} with no upper boughtAt bound. Pages through Allegro API.
     */
    public AllegroSoldItemsResult getSoldItemsFrom(LocalDate from) {
        if (from == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Podaj datę początkową (from).");
        }
        Instant boughtAtFrom = from.atStartOfDay(ZONE).toInstant();
        return getSoldItems(boughtAtFrom, null, 0, 100, true);
    }

    private AllegroSoldItemsResult getSoldItems(
            Instant boughtAtFrom,
            Instant boughtAtTo,
            int offset,
            int limit,
            boolean pageAll) {
        List<AllegroAccount> accounts = accountService.listConnectedAccounts();
        if (accounts.isEmpty()) {
            return new AllegroSoldItemsResult(List.of(), List.of());
        }

        List<AllegroSoldItemDto> orders = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (AllegroAccount account : accounts) {
            try {
                String token = authService.getValidAccessTokenForAccount(account);
                int pageOffset = Math.max(0, offset);
                int pageLimit = Math.min(Math.max(limit, 1), 100);
                while (true) {
                    AllegroCheckoutFormsResponse response = allegroApiClient.getCheckoutForms(
                            account.getApiBaseUrl(),
                            token,
                            account.getUserAgent(),
                            pageOffset,
                            pageLimit,
                            boughtAtFrom,
                            boughtAtTo);
                    if (response == null || response.checkoutForms() == null || response.checkoutForms().isEmpty()) {
                        break;
                    }
                    for (AllegroCheckoutForm form : response.checkoutForms()) {
                        orders.add(soldItemMapper.toDto(form, account.getId(), account.getName(), null));
                    }
                    if (!pageAll || response.checkoutForms().size() < pageLimit) {
                        break;
                    }
                    pageOffset += pageLimit;
                }
            } catch (RuntimeException ex) {
                if (isForbidden(ex)) {
                    log.info(
                            "Brak uprawnień Allegro do zamówień dla konta id={} name='{}'",
                            account.getId(),
                            account.getName());
                    warnings.add(accessDeniedMessage(account.getName()));
                } else {
                    log.warn(
                            "Nie udało się pobrać zamówień dla konta Allegro id={} name='{}': {}",
                            account.getId(),
                            account.getName(),
                            ex.toString());
                }
            }
        }

        Map<String, AllegroSoldInvoice> soldInvoices = loadSoldInvoices(orders.stream()
                .map(AllegroSoldItemDto::orderId)
                .filter(Objects::nonNull)
                .toList());

        List<AllegroSoldItemDto> resultOrders = orders;
        if (!soldInvoices.isEmpty()) {
            List<AllegroSoldItemDto> enriched = new ArrayList<>(orders.size());
            for (AllegroSoldItemDto order : orders) {
                AllegroSoldInvoice saved = soldInvoices.get(order.orderId());
                if (saved == null) {
                    enriched.add(order);
                    continue;
                }
                AllegroSoldItemDto withInvoice = saved.hasIssuedInvoice()
                        ? soldItemMapper.withInvoiceNo(order, saved.getInvoiceNo())
                        : order;
                if (saved.getIssueError() != null && !saved.getIssueError().isBlank()) {
                    withInvoice = soldItemMapper.withIssueError(withInvoice, saved.getIssueError());
                }
                enriched.add(withInvoice);
            }
            resultOrders = enriched;
        }
        return new AllegroSoldItemsResult(resultOrders, warnings);
    }

    static String accessDeniedMessage(String accountName) {
        String label = accountName == null || accountName.isBlank() ? "Allegro" : accountName;
        return "Konto \"" + label + "\": brak uprawnień do zamówień w Allegro. "
                + "W Developer Apps dodaj scope allegro:api:orders:read.";
    }

    private static boolean isForbidden(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof HttpStatusCodeException httpEx && httpEx.getStatusCode().value() == 403) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private Map<String, AllegroSoldInvoice> loadSoldInvoices(List<String> orderIds) {
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        return soldInvoiceRepository.findByOrderIdIn(orderIds).stream()
                .collect(Collectors.toMap(AllegroSoldInvoice::getOrderId, invoice -> invoice, (a, b) -> a));
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
