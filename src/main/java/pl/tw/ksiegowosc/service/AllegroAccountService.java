package pl.tw.ksiegowosc.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import pl.tw.ksiegowosc.dto.AllegroAccountDto;
import pl.tw.ksiegowosc.dto.AllegroClientCredentials;
import pl.tw.ksiegowosc.entity.AllegroAccount;
import pl.tw.ksiegowosc.repository.AllegroAccountRepository;
import pl.tw.ksiegowosc.repository.AllegroTokenRepository;

@Service
public class AllegroAccountService {

    public static final String DEFAULT_API_BASE_URL = "https://api.allegro.pl";
    public static final String DEFAULT_AUTH_URL = "https://allegro.pl";
    public static final String DEFAULT_USER_AGENT =
            "Ksiegowosc/0.0.1 (+https://ksiegowosc-a0yu.onrender.com)";

    private static final int PREFIX_MAX = 20;

    private final AllegroAccountRepository accountRepository;
    private final AllegroTokenRepository tokenRepository;
    private final InvoicesService invoicesService;
    private final CurrentUserApiCredentialsService credentialsService;
    private final Clock clock;

    public AllegroAccountService(
            AllegroAccountRepository accountRepository,
            AllegroTokenRepository tokenRepository,
            InvoicesService invoicesService,
            CurrentUserApiCredentialsService credentialsService,
            Clock clock) {
        this.accountRepository = accountRepository;
        this.tokenRepository = tokenRepository;
        this.invoicesService = invoicesService;
        this.credentialsService = credentialsService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AllegroAccountDto> listAccounts() {
        Long userId = credentialsService.requireCurrentUserId();
        List<AllegroAccount> accounts = accountRepository.findByUserIdOrderByNameAsc(userId);
        Set<Long> connectedIds = tokenRepository.findAllById(
                        accounts.stream().map(AllegroAccount::getId).toList())
                .stream()
                .map(pl.tw.ksiegowosc.entity.AllegroToken::getAccountId)
                .collect(Collectors.toSet());
        return accounts.stream()
                .map(account -> toDto(account, connectedIds.contains(account.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AllegroAccount> listConnectedAccounts() {
        Long userId = credentialsService.requireCurrentUserId();
        return accountRepository.findByUserIdOrderByNameAsc(userId).stream()
                .filter(account -> tokenRepository.existsById(account.getId()))
                .toList();
    }

    @Transactional
    public AllegroAccountDto addAccount(
            String name,
            String clientId,
            String clientSecret,
            String invoicePrefix,
            String apiBaseUrl,
            String authUrl,
            String userAgent) {
        Long userId = credentialsService.requireCurrentUserId();
        if (!hasText(name)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Podaj nazwę konta Allegro.");
        }
        if (!hasText(clientId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Podaj Allegro Client ID.");
        }
        if (!hasText(clientSecret)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Podaj Allegro Client Secret.");
        }
        String normalizedPrefix = normalizePrefix(invoicePrefix);
        String trimmedClientId = clientId.trim();
        if (accountRepository.existsByUserIdAndClientId(userId, trimmedClientId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Konto z tym Client ID już istnieje.");
        }

        Instant now = Instant.now(clock);
        AllegroAccount account = new AllegroAccount();
        account.setUserId(userId);
        account.setName(name.trim());
        account.setClientId(trimmedClientId);
        account.setClientSecret(clientSecret.trim());
        account.setInvoicePrefix(normalizedPrefix);
        account.setApiBaseUrl(requireHttpsUrl(apiBaseUrl, "Allegro API Base URL"));
        account.setAuthUrl(requireHttpsUrl(authUrl, "Allegro Auth URL"));
        account.setUserAgent(requireUserAgent(userAgent));
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        AllegroAccount saved = accountRepository.save(account);
        return toDto(saved, false);
    }

    @Transactional
    public AllegroAccountDto updateAccount(
            Long accountId,
            String name,
            String invoicePrefix,
            String apiBaseUrl,
            String authUrl,
            String userAgent) {
        AllegroAccount account = requireOwnedAccount(accountId);
        if (!hasText(name)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Podaj nazwę konta Allegro.");
        }
        account.setName(name.trim());
        account.setInvoicePrefix(normalizePrefix(invoicePrefix));
        account.setApiBaseUrl(requireHttpsUrl(apiBaseUrl, "Allegro API Base URL"));
        account.setAuthUrl(requireHttpsUrl(authUrl, "Allegro Auth URL"));
        account.setUserAgent(requireUserAgent(userAgent));
        account.setUpdatedAt(Instant.now(clock));
        return toDto(account, tokenRepository.existsById(account.getId()));
    }

    @Transactional
    public void deleteAccount(Long accountId) {
        AllegroAccount account = requireOwnedAccount(accountId);
        accountRepository.delete(account);
    }

    @Transactional(readOnly = true)
    public String allocateInvoiceNo(Long accountId, LocalDate docDate) {
        AllegroAccount account = requireOwnedAccount(accountId);
        if (!hasText(account.getInvoicePrefix())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Ustaw prefiks faktury dla konta Allegro w Ustawieniach API.");
        }
        return invoicesService.nextInvoiceNoFromMerit(account.getInvoicePrefix(), docDate);
    }

    @Transactional(readOnly = true)
    public AllegroAccount requireOwnedAccount(Long accountId) {
        Long userId = credentialsService.requireCurrentUserId();
        return accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleziono konta Allegro."));
    }

    @Transactional(readOnly = true)
    public AllegroAccount requireOwnedAccountForUser(Long accountId, Long userId) {
        return accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nieprawidłowe konto Allegro."));
    }

    public AllegroClientCredentials toClientCredentials(AllegroAccount account) {
        return new AllegroClientCredentials(
                account.getClientId(),
                account.getClientSecret(),
                stripTrailingSlash(account.getApiBaseUrl()),
                stripTrailingSlash(account.getAuthUrl()),
                account.getUserAgent().trim());
    }

    private static String normalizePrefix(String invoicePrefix) {
        if (!hasText(invoicePrefix)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Podaj prefiks faktury.");
        }
        String trimmed = invoicePrefix.trim();
        if (trimmed.length() > PREFIX_MAX) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Prefiks faktury może mieć maksymalnie " + PREFIX_MAX + " znaków.");
        }
        if (trimmed.contains("/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Prefiks faktury nie może zawierać '/'.");
        }
        return trimmed;
    }

    private static String requireHttpsUrl(String value, String label) {
        if (!hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Podaj " + label + ".");
        }
        String trimmed = stripTrailingSlash(value.trim());
        if (!trimmed.startsWith("https://")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " musi zaczynać się od https://");
        }
        return trimmed;
    }

    private static String requireUserAgent(String userAgent) {
        if (!hasText(userAgent)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Podaj Allegro User-Agent.");
        }
        return userAgent.trim();
    }

    private static String stripTrailingSlash(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static AllegroAccountDto toDto(AllegroAccount account, boolean connected) {
        return new AllegroAccountDto(
                account.getId(),
                account.getName(),
                account.getClientId(),
                account.getInvoicePrefix(),
                account.getApiBaseUrl(),
                account.getAuthUrl(),
                account.getUserAgent(),
                hasText(account.getClientSecret()),
                connected);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
