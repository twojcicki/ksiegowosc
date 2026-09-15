package pl.tw.ksiegowosc.service;

import java.time.Clock;
import java.time.Instant;
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

    private final AllegroAccountRepository accountRepository;
    private final AllegroTokenRepository tokenRepository;
    private final CurrentUserApiCredentialsService credentialsService;
    private final Clock clock;

    public AllegroAccountService(
            AllegroAccountRepository accountRepository,
            AllegroTokenRepository tokenRepository,
            CurrentUserApiCredentialsService credentialsService,
            Clock clock) {
        this.accountRepository = accountRepository;
        this.tokenRepository = tokenRepository;
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
    public AllegroAccountDto addAccount(String name, String clientId, String clientSecret) {
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
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        AllegroAccount saved = accountRepository.save(account);
        return toDto(saved, false);
    }

    @Transactional
    public void deleteAccount(Long accountId) {
        AllegroAccount account = requireOwnedAccount(accountId);
        accountRepository.delete(account);
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

    private static AllegroAccountDto toDto(AllegroAccount account, boolean connected) {
        return new AllegroAccountDto(
                account.getId(),
                account.getName(),
                account.getClientId(),
                hasText(account.getClientSecret()),
                connected);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
