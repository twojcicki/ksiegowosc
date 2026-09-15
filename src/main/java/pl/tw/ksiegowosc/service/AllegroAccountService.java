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
    public AllegroAccountDto addAccount(String name, String clientId, String clientSecret, String invoicePrefix) {
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
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        AllegroAccount saved = accountRepository.save(account);
        return toDto(saved, false);
    }

    @Transactional
    public AllegroAccountDto updateInvoicePrefix(Long accountId, String invoicePrefix) {
        AllegroAccount account = requireOwnedAccount(accountId);
        account.setInvoicePrefix(normalizePrefix(invoicePrefix));
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
        return new AllegroClientCredentials(account.getClientId(), account.getClientSecret());
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

    private static AllegroAccountDto toDto(AllegroAccount account, boolean connected) {
        return new AllegroAccountDto(
                account.getId(),
                account.getName(),
                account.getClientId(),
                account.getInvoicePrefix(),
                hasText(account.getClientSecret()),
                connected);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
