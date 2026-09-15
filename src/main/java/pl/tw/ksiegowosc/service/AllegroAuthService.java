package pl.tw.ksiegowosc.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import pl.tw.ksiegowosc.config.AllegroApiProperties;
import pl.tw.ksiegowosc.dto.AllegroClientCredentials;
import pl.tw.ksiegowosc.dto.AllegroTokenResponse;
import pl.tw.ksiegowosc.entity.AllegroAccount;
import pl.tw.ksiegowosc.entity.AllegroToken;
import pl.tw.ksiegowosc.mapper.AllegroTokenMapper;
import pl.tw.ksiegowosc.repository.AllegroTokenRepository;

@Service
public class AllegroAuthService {

    private static final Duration EXPIRY_MARGIN = Duration.ofSeconds(60);

    private final AllegroApiProperties properties;
    private final AllegroTokenRepository tokenRepository;
    private final AllegroAccountService accountService;
    private final CurrentUserApiCredentialsService credentialsService;
    private final RestClient allegroAuthRestClient;
    private final AllegroTokenMapper tokenMapper;
    private final Clock clock;

    public AllegroAuthService(
            AllegroApiProperties properties,
            AllegroTokenRepository tokenRepository,
            AllegroAccountService accountService,
            CurrentUserApiCredentialsService credentialsService,
            @Qualifier("allegroAuthRestClient") RestClient allegroAuthRestClient,
            AllegroTokenMapper tokenMapper,
            Clock clock) {
        this.properties = properties;
        this.tokenRepository = tokenRepository;
        this.accountService = accountService;
        this.credentialsService = credentialsService;
        this.allegroAuthRestClient = allegroAuthRestClient;
        this.tokenMapper = tokenMapper;
        this.clock = clock;
    }

    public boolean isConnected() {
        return !accountService.listConnectedAccounts().isEmpty();
    }

    public boolean isConnected(Long accountId) {
        accountService.requireOwnedAccount(accountId);
        return tokenRepository.existsById(accountId);
    }

    public String buildAuthorizationUrl(Long accountId) {
        AllegroAccount account = accountService.requireOwnedAccount(accountId);
        Long userId = credentialsService.requireCurrentUserId();
        AllegroClientCredentials client = accountService.toClientCredentials(account);
        String state = encodeState(userId, account.getId());
        return properties.authUrl()
                + "/auth/oauth/authorize?response_type=code"
                + "&client_id=" + encode(client.clientId())
                + "&redirect_uri=" + encode(resolveRedirectUri())
                + "&scope=" + encode(properties.scopes())
                + "&state=" + encode(state);
    }

    /**
     * Redirect URI must match the one registered in Allegro Developer Apps exactly.
     * Prefer {@code ALLEGRO_REDIRECT_URI}; otherwise derive from the current request (Render-friendly).
     */
    public String resolveRedirectUri() {
        String configured = properties.redirectUri();
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        try {
            return ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path("/api/allegro/auth/callback")
                    .build()
                    .toUriString();
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Ustaw zmienną ALLEGRO_REDIRECT_URI (brak kontekstu HTTP do auto-wykrycia callbacku).");
        }
    }

    @Transactional
    public void exchangeAuthorizationCode(String code, String state) {
        OAuthState parsed = parseState(state);
        credentialsService.requireUserById(parsed.userId());
        AllegroAccount account = accountService.requireOwnedAccountForUser(parsed.accountId(), parsed.userId());
        AllegroClientCredentials client = accountService.toClientCredentials(account);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", resolveRedirectUri());
        saveToken(account.getId(), requestToken(client, form));
    }

    @Transactional
    public String getValidAccessToken(Long accountId) {
        AllegroAccount account = accountService.requireOwnedAccount(accountId);
        return getValidAccessTokenForAccount(account);
    }

    @Transactional
    public String getValidAccessTokenForAccount(AllegroAccount account) {
        AllegroToken token = tokenRepository.findById(account.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Połącz konto Allegro \"" + account.getName() + "\" w Ustawieniach API."));

        Instant now = clock.instant();
        if (token.getExpiresAt().isAfter(now.plus(EXPIRY_MARGIN))) {
            return token.getAccessToken();
        }
        return refreshAccessToken(account, token);
    }

    @Transactional
    protected String refreshAccessToken(AllegroAccount account, AllegroToken token) {
        AllegroClientCredentials client = accountService.toClientCredentials(account);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", token.getRefreshToken());
        AllegroTokenResponse response = requestToken(client, form);
        tokenMapper.updateEntity(response, token, clock);
        tokenRepository.save(token);
        return token.getAccessToken();
    }

    private void saveToken(Long accountId, AllegroTokenResponse response) {
        AllegroToken existing = tokenRepository.findById(accountId).orElse(null);
        AllegroToken token = tokenMapper.apply(response, existing, accountId, clock);
        tokenRepository.save(token);
    }

    private AllegroTokenResponse requestToken(AllegroClientCredentials client, MultiValueMap<String, String> form) {
        String credentials = client.clientId() + ":" + client.clientSecret();
        String basicAuth = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

        return allegroAuthRestClient.post()
                .uri("/auth/oauth/token")
                .header(HttpHeaders.AUTHORIZATION, "Basic " + basicAuth)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(AllegroTokenResponse.class);
    }

    static String encodeState(Long userId, Long accountId) {
        return userId + ":" + accountId;
    }

    private static OAuthState parseState(String state) {
        if (state == null || state.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Brak parametru state w callbacku Allegro.");
        }
        String[] parts = state.trim().split(":");
        if (parts.length != 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nieprawidłowy parametr state w callbacku Allegro.");
        }
        try {
            return new OAuthState(Long.valueOf(parts[0]), Long.valueOf(parts[1]));
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nieprawidłowy parametr state w callbacku Allegro.");
        }
    }

    private static String encode(String value) {
        // OAuth query params should use %20, not + (Allegro rejects mismatched encoding).
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private record OAuthState(Long userId, Long accountId) {
    }
}
