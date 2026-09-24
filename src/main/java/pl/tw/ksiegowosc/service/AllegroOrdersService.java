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
        List<AllegroAccount> accounts = accountService.listConnectedAccounts();
        if (accounts.isEmpty()) {
            return new AllegroSoldItemsResult(List.of(), List.of());
        }

        Instant boughtAtFrom = from.atStartOfDay(ZONE).toInstant();
        Instant boughtAtTo = to.plusDays(1).atStartOfDay(ZONE).toInstant().minusMillis(1);

        List<AllegroSoldItemDto> orders = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (AllegroAccount account : accounts) {
            try {
                String token = authService.getValidAccessTokenForAccount(account);
                AllegroCheckoutFormsResponse response = allegroApiClient.getCheckoutForms(
                        account.getApiBaseUrl(),
                        token,
                        account.getUserAgent(),
                        offset,
                        limit,
                        boughtAtFrom,
                        boughtAtTo);
                if (response == null || response.checkoutForms() == null) {
                    continue;
                }
                for (AllegroCheckoutForm form : response.checkoutForms()) {
                    orders.add(soldItemMapper.toDto(form, account.getId(), account.getName(), null));
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

        Map<String, String> invoiceNos = loadInvoiceNos(orders.stream()
                .map(AllegroSoldItemDto::orderId)
                .filter(Objects::nonNull)
                .toList());

        List<AllegroSoldItemDto> resultOrders = orders;
        if (!invoiceNos.isEmpty()) {
            List<AllegroSoldItemDto> withInvoices = new ArrayList<>(orders.size());
            for (AllegroSoldItemDto order : orders) {
                withInvoices.add(soldItemMapper.withInvoiceNo(order, invoiceNos.get(order.orderId())));
            }
            resultOrders = withInvoices;
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
