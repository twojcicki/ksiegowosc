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
import pl.tw.ksiegowosc.dto.AllegroTokenResponse;
import pl.tw.ksiegowosc.entity.AllegroToken;
import pl.tw.ksiegowosc.mapper.AllegroTokenMapper;
import pl.tw.ksiegowosc.repository.AllegroTokenRepository;

@Service
public class AllegroAuthService {

    private static final Duration EXPIRY_MARGIN = Duration.ofSeconds(60);

    private final AllegroApiProperties properties;
    private final AllegroTokenRepository tokenRepository;
    private final RestClient allegroAuthRestClient;
    private final AllegroTokenMapper tokenMapper;
    private final Clock clock;

    public AllegroAuthService(
            AllegroApiProperties properties,
            AllegroTokenRepository tokenRepository,
            @Qualifier("allegroAuthRestClient") RestClient allegroAuthRestClient,
            AllegroTokenMapper tokenMapper,
            Clock clock) {
        this.properties = properties;
        this.tokenRepository = tokenRepository;
        this.allegroAuthRestClient = allegroAuthRestClient;
        this.tokenMapper = tokenMapper;
        this.clock = clock;
    }

    public boolean isConnected() {
        return tokenRepository.findById(AllegroToken.SINGLETON_ID).isPresent();
    }

    public String buildAuthorizationUrl() {
        return properties.authUrl()
                + "/auth/oauth/authorize?response_type=code"
                + "&client_id=" + encode(properties.clientId())
                + "&redirect_uri=" + encode(properties.redirectUri())
                + "&scope=" + encode(properties.scopes());
    }

    @Transactional
    public void exchangeAuthorizationCode(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", properties.redirectUri());
        saveToken(requestToken(form));
    }

    @Transactional
    public String getValidAccessToken() {
        AllegroToken token = tokenRepository.findById(AllegroToken.SINGLETON_ID)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Połącz konto Allegro przez /api/allegro/auth/connect."));

        Instant now = clock.instant();
        if (token.getExpiresAt().isAfter(now.plus(EXPIRY_MARGIN))) {
            return token.getAccessToken();
        }
        return refreshAccessToken(token);
    }

    @Transactional
    protected String refreshAccessToken(AllegroToken token) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", token.getRefreshToken());
        AllegroTokenResponse response = requestToken(form);
        tokenMapper.updateEntity(response, token, clock);
        tokenRepository.save(token);
        return token.getAccessToken();
    }

    private void saveToken(AllegroTokenResponse response) {
        AllegroToken existing = tokenRepository.findById(AllegroToken.SINGLETON_ID).orElse(null);
        AllegroToken token = tokenMapper.apply(response, existing, clock);
        tokenRepository.save(token);
    }

    private AllegroTokenResponse requestToken(MultiValueMap<String, String> form) {
        String credentials = properties.clientId() + ":" + properties.clientSecret();
        String basicAuth = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

        return allegroAuthRestClient.post()
                .uri("/auth/oauth/token")
                .header(HttpHeaders.AUTHORIZATION, "Basic " + basicAuth)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(AllegroTokenResponse.class);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
