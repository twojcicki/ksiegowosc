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

import pl.tw.ksiegowosc.dto.AllegroClientCredentials;
import pl.tw.ksiegowosc.dto.MeritCredentials;
import pl.tw.ksiegowosc.dto.UserApiSettingsDto;
import pl.tw.ksiegowosc.entity.AppUser;
import pl.tw.ksiegowosc.entity.UserApiCredentials;
import pl.tw.ksiegowosc.repository.AllegroTokenRepository;
import pl.tw.ksiegowosc.repository.AppUserRepository;
import pl.tw.ksiegowosc.repository.UserApiCredentialsRepository;

@Service
public class CurrentUserApiCredentialsService {

    private final AppUserRepository appUserRepository;
    private final UserApiCredentialsRepository credentialsRepository;
    private final AllegroTokenRepository allegroTokenRepository;
    private final Clock clock;

    public CurrentUserApiCredentialsService(
            AppUserRepository appUserRepository,
            UserApiCredentialsRepository credentialsRepository,
            AllegroTokenRepository allegroTokenRepository,
            Clock clock) {
        this.appUserRepository = appUserRepository;
        this.credentialsRepository = credentialsRepository;
        this.allegroTokenRepository = allegroTokenRepository;
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
        boolean connected = allegroTokenRepository.existsById(userId);
        if (credentials == null) {
            return new UserApiSettingsDto(null, false, null, false, connected);
        }
        return new UserApiSettingsDto(
                credentials.getMeritApiId(),
                hasText(credentials.getMeritApiKey()),
                credentials.getAllegroClientId(),
                hasText(credentials.getAllegroClientSecret()),
                connected);
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

    @Transactional
    public void saveAllegroCredentials(String clientId, String clientSecretOrBlank) {
        UserApiCredentials credentials = getOrCreate(requireCurrentUserId());
        if (!hasText(clientId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Podaj Allegro Client ID.");
        }
        credentials.setAllegroClientId(clientId.trim());
        if (hasText(clientSecretOrBlank)) {
            credentials.setAllegroClientSecret(clientSecretOrBlank.trim());
        } else if (!hasText(credentials.getAllegroClientSecret())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Podaj Allegro Client Secret.");
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

    public AllegroClientCredentials requireAllegroClientCredentials() {
        return requireAllegroClientCredentialsForUser(requireCurrentUserId());
    }

    public AllegroClientCredentials requireAllegroClientCredentialsForUser(Long userId) {
        UserApiCredentials credentials = credentialsRepository.findById(userId)
                .orElseThrow(this::missingAllegro);
        if (!hasText(credentials.getAllegroClientId()) || !hasText(credentials.getAllegroClientSecret())) {
            throw missingAllegro();
        }
        return new AllegroClientCredentials(
                credentials.getAllegroClientId().trim(),
                credentials.getAllegroClientSecret().trim());
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

    private ResponseStatusException missingAllegro() {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Uzupełnij klucze Allegro w Ustawieniach API.");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
