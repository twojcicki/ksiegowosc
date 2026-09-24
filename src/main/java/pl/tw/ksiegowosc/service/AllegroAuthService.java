package pl.tw.ksiegowosc.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
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

    private static final Logger log = LoggerFactory.getLogger(AllegroAuthService.class);
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
        String redirectUri = resolveRedirectUri();
        String scopes = properties.scopes() == null ? "" : properties.scopes().trim();
        String url = client.authUrl()
                + "/auth/oauth/authorize?response_type=code"
                + "&client_id=" + encode(client.clientId())
                + "&redirect_uri=" + encode(redirectUri)
                + (scopes.isEmpty() ? "" : "&scope=" + encode(scopes))
                + "&state=" + encode(state);
        log.info(
                "Allegro OAuth authorize: accountId={} name='{}' userId={} clientId={} authUrl={} apiBaseUrl={} redirectUri={} scopes={} state={}",
                account.getId(),
                account.getName(),
                userId,
                client.clientId(),
                client.authUrl(),
                client.apiBaseUrl(),
                redirectUri,
                scopes.isEmpty() ? "(omitted)" : scopes,
                state);
        return url;
    }

    /**
     * Allegro redirected back with {@code error=…} instead of {@code code=…}.
     * Returns a short Polish message for the UI; details go to the application log.
     */
    public String describeAuthorizationError(String error, String errorDescription, String state) {
        OAuthState parsed = null;
        try {
            if (StringUtils.hasText(state)) {
                parsed = parseState(state);
            }
        } catch (ResponseStatusException ignored) {
            // still log raw state below
        }

        String accountHint = parsed == null
                ? "state=" + state
                : "userId=" + parsed.userId() + " accountId=" + parsed.accountId();
        if (parsed != null) {
            try {
                AllegroAccount account = accountService.requireOwnedAccountForUser(parsed.accountId(), parsed.userId());
                AllegroClientCredentials client = accountService.toClientCredentials(account);
                accountHint = accountHint
                        + " name='" + account.getName() + "'"
                        + " clientId=" + client.clientId()
                        + " authUrl=" + client.authUrl()
                        + " apiBaseUrl=" + client.apiBaseUrl()
                        + " redirectUri=" + resolveRedirectUri();
            } catch (RuntimeException ex) {
                log.warn("Allegro OAuth error callback: could not load account for {}", accountHint, ex);
            }
        }

        log.warn(
                "Allegro OAuth rejected authorize: error={} description={} {} (check Client ID vs Auth URL: sandbox vs production)",
                error,
                errorDescription,
                accountHint);

        String detail = StringUtils.hasText(errorDescription) ? errorDescription : error;
        if (!StringUtils.hasText(detail)) {
            detail = "nieznany błąd OAuth";
        }
        return "Allegro odrzuciło autoryzację (" + detail + "). Sprawdź Client ID i Auth URL (produkcja vs sandbox).";
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
        String redirectUri = resolveRedirectUri();

        log.info(
                "Allegro OAuth token exchange: accountId={} name='{}' userId={} clientId={} authUrl={} redirectUri={}",
                account.getId(),
                account.getName(),
                parsed.userId(),
                client.clientId(),
                client.authUrl(),
                redirectUri);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", redirectUri);
        try {
            saveToken(account.getId(), requestToken(client, form));
            log.info("Allegro OAuth token exchange succeeded: accountId={}", account.getId());
        } catch (RestClientResponseException ex) {
            log.warn(
                    "Allegro OAuth token exchange failed: accountId={} clientId={} authUrl={} status={} body={}",
                    account.getId(),
                    client.clientId(),
                    client.authUrl(),
                    ex.getStatusCode().value(),
                    truncateForLog(ex.getResponseBodyAsString()));
            throw ex;
        }
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
                .uri(client.authUrl() + "/auth/oauth/token")
                .header(HttpHeaders.AUTHORIZATION, "Basic " + basicAuth)
                .header(HttpHeaders.USER_AGENT, client.userAgent())
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

    static String truncateForLog(String body) {
        if (body == null) {
            return "";
        }
        String trimmed = body.strip();
        if (trimmed.length() <= 500) {
            return trimmed;
        }
        return trimmed.substring(0, 500) + "…";
    }

    private record OAuthState(Long userId, Long accountId) {
    }
}
