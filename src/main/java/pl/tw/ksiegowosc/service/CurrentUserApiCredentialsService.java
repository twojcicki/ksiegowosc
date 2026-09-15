package pl.tw.ksiegowosc.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import pl.tw.ksiegowosc.dto.MeritCredentials;
import pl.tw.ksiegowosc.dto.UserApiSettingsDto;
import pl.tw.ksiegowosc.entity.AppUser;
import pl.tw.ksiegowosc.entity.UserApiCredentials;
import pl.tw.ksiegowosc.repository.AppUserRepository;
import pl.tw.ksiegowosc.repository.UserApiCredentialsRepository;

@Service
public class CurrentUserApiCredentialsService {

    private final AppUserRepository appUserRepository;
    private final UserApiCredentialsRepository credentialsRepository;
    private final Clock clock;

    public CurrentUserApiCredentialsService(
            AppUserRepository appUserRepository,
            UserApiCredentialsRepository credentialsRepository,
            Clock clock) {
        this.appUserRepository = appUserRepository;
        this.credentialsRepository = credentialsRepository;
        this.clock = clock;
    }

    public Long requireCurrentUserId() {
        return requireCurrentUser().getId();
    }

    public AppUser requireCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Wymagane logowanie.");
        }
        Object principal = authentication.getPrincipal();
        String login;
        if (principal instanceof UserDetails userDetails) {
            login = userDetails.getUsername();
        } else if (principal instanceof String name) {
            login = name;
        } else {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Wymagane logowanie.");
        }
        return appUserRepository.findByLogin(login)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Nie znaleziono użytkownika."));
    }

    public AppUser requireUserById(Long userId) {
        return appUserRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nieprawidłowy użytkownik."));
    }

    @Transactional(readOnly = true)
    public UserApiSettingsDto getSettings() {
        Long userId = requireCurrentUserId();
        UserApiCredentials credentials = credentialsRepository.findById(userId).orElse(null);
        if (credentials == null) {
            return new UserApiSettingsDto(null, false);
        }
        return new UserApiSettingsDto(
                credentials.getMeritApiId(),
                hasText(credentials.getMeritApiKey()));
    }

    @Transactional
    public void saveMeritCredentials(String apiId, String apiKeyOrBlank) {
        UserApiCredentials credentials = getOrCreate(requireCurrentUserId());
        if (!hasText(apiId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Podaj Merit Api Id.");
        }
        credentials.setMeritApiId(apiId.trim());
        if (hasText(apiKeyOrBlank)) {
            credentials.setMeritApiKey(apiKeyOrBlank.trim());
        } else if (!hasText(credentials.getMeritApiKey())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Podaj Merit Api Key.");
        }
        credentials.setUpdatedAt(Instant.now(clock));
        credentialsRepository.save(credentials);
    }

    public MeritCredentials requireMeritCredentials() {
        return requireMeritCredentialsForUser(requireCurrentUserId());
    }

    public MeritCredentials requireMeritCredentialsForUser(Long userId) {
        UserApiCredentials credentials = credentialsRepository.findById(userId)
                .orElseThrow(this::missingMerit);
        if (!hasText(credentials.getMeritApiId()) || !hasText(credentials.getMeritApiKey())) {
            throw missingMerit();
        }
        return new MeritCredentials(credentials.getMeritApiId().trim(), credentials.getMeritApiKey().trim());
    }

    private UserApiCredentials getOrCreate(Long userId) {
        Optional<UserApiCredentials> existing = credentialsRepository.findById(userId);
        if (existing.isPresent()) {
            return existing.get();
        }
        UserApiCredentials created = new UserApiCredentials();
        created.setUserId(userId);
        created.setUpdatedAt(Instant.now(clock));
        return created;
    }

    private ResponseStatusException missingMerit() {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Uzupełnij klucze Merit w Ustawieniach API.");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
