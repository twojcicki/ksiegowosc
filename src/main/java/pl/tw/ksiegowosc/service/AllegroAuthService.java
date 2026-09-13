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

import pl.tw.ksiegowosc.config.AllegroApiProperties;
import pl.tw.ksiegowosc.dto.AllegroClientCredentials;
import pl.tw.ksiegowosc.dto.AllegroTokenResponse;
import pl.tw.ksiegowosc.entity.AllegroToken;
import pl.tw.ksiegowosc.mapper.AllegroTokenMapper;
import pl.tw.ksiegowosc.repository.AllegroTokenRepository;

@Service
public class AllegroAuthService {

    private static final Duration EXPIRY_MARGIN = Duration.ofSeconds(60);

    private final AllegroApiProperties properties;
    private final AllegroTokenRepository tokenRepository;
    private final CurrentUserApiCredentialsService credentialsService;
    private final RestClient allegroAuthRestClient;
    private final AllegroTokenMapper tokenMapper;
    private final Clock clock;

    public AllegroAuthService(
            AllegroApiProperties properties,
            AllegroTokenRepository tokenRepository,
            CurrentUserApiCredentialsService credentialsService,
            @Qualifier("allegroAuthRestClient") RestClient allegroAuthRestClient,
            AllegroTokenMapper tokenMapper,
            Clock clock) {
        this.properties = properties;
        this.tokenRepository = tokenRepository;
        this.credentialsService = credentialsService;
        this.allegroAuthRestClient = allegroAuthRestClient;
        this.tokenMapper = tokenMapper;
        this.clock = clock;
    }

    public boolean isConnected() {
        return tokenRepository.existsById(credentialsService.requireCurrentUserId());
    }

    public String buildAuthorizationUrl() {
        Long userId = credentialsService.requireCurrentUserId();
        AllegroClientCredentials client = credentialsService.requireAllegroClientCredentials();
        return properties.authUrl()
                + "/auth/oauth/authorize?response_type=code"
                + "&client_id=" + encode(client.clientId())
                + "&redirect_uri=" + encode(properties.redirectUri())
                + "&scope=" + encode(properties.scopes())
                + "&state=" + encode(String.valueOf(userId));
    }

    @Transactional
    public void exchangeAuthorizationCode(String code, String state) {
        Long userId = parseUserId(state);
        credentialsService.requireUserById(userId);
        AllegroClientCredentials client = credentialsService.requireAllegroClientCredentialsForUser(userId);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", properties.redirectUri());
        saveToken(userId, requestToken(client, form));
    }

    @Transactional
    public void disconnect() {
        Long userId = credentialsService.requireCurrentUserId();
        tokenRepository.deleteById(userId);
    }

    @Transactional
    public String getValidAccessToken() {
        Long userId = credentialsService.requireCurrentUserId();
        AllegroToken token = tokenRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Połącz konto Allegro przez /api/allegro/auth/connect."));

        Instant now = clock.instant();
        if (token.getExpiresAt().isAfter(now.plus(EXPIRY_MARGIN))) {
            return token.getAccessToken();
        }
        return refreshAccessToken(userId, token);
    }

    @Transactional
    protected String refreshAccessToken(Long userId, AllegroToken token) {
        AllegroClientCredentials client = credentialsService.requireAllegroClientCredentialsForUser(userId);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", token.getRefreshToken());
        AllegroTokenResponse response = requestToken(client, form);
        tokenMapper.updateEntity(response, token, clock);
        tokenRepository.save(token);
        return token.getAccessToken();
    }

    private void saveToken(Long userId, AllegroTokenResponse response) {
        AllegroToken existing = tokenRepository.findById(userId).orElse(null);
        AllegroToken token = tokenMapper.apply(response, existing, userId, clock);
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

    private static Long parseUserId(String state) {
        if (state == null || state.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Brak parametru state w callbacku Allegro.");
        }
        try {
            return Long.valueOf(state.trim());
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nieprawidłowy parametr state w callbacku Allegro.");
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
