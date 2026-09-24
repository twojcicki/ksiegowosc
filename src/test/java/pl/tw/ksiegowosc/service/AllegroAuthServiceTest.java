package pl.tw.ksiegowosc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import pl.tw.ksiegowosc.config.AllegroApiProperties;
import pl.tw.ksiegowosc.dto.AllegroClientCredentials;
import pl.tw.ksiegowosc.entity.AllegroAccount;
import pl.tw.ksiegowosc.entity.AllegroToken;
import pl.tw.ksiegowosc.entity.AppUser;
import pl.tw.ksiegowosc.mapper.MapperFixtures;
import pl.tw.ksiegowosc.repository.AllegroTokenRepository;

@ExtendWith(MockitoExtension.class)
class AllegroAuthServiceTest {

    private static final Long USER_ID = 42L;
    private static final Long ACCOUNT_ID = 7L;
    private static final AllegroApiProperties PROPERTIES = new AllegroApiProperties(
            "http://localhost:8080/api/allegro/auth/callback",
            "allegro:api:sale:offers:read allegro:api:orders:read");
    private static final String AUTH_URL = "https://allegro.pl.allegrosandbox.pl";
    private static final String USER_AGENT = "Ksiegowosc-Test/0.0.1 (+https://example.test)";
    private static final AllegroClientCredentials CLIENT = new AllegroClientCredentials(
            "client-id",
            "client-secret",
            "https://api.allegro.pl.allegrosandbox.pl",
            AUTH_URL,
            USER_AGENT);

    @Mock
    private AllegroTokenRepository tokenRepository;

    @Mock
    private AllegroAccountService accountService;

    @Mock
    private CurrentUserApiCredentialsService credentialsService;

    private final AtomicReference<AllegroToken> storedToken = new AtomicReference<>();
    private AllegroAccount account;
    private MockRestServiceServer server;
    private AllegroAuthService authService;
    private Clock clock;

    @BeforeEach
    void setUp() {
        account = new AllegroAccount();
        account.setId(ACCOUNT_ID);
        account.setUserId(USER_ID);
        account.setName("Sklep");
        account.setClientId(CLIENT.clientId());
        account.setClientSecret(CLIENT.clientSecret());
        account.setApiBaseUrl(CLIENT.apiBaseUrl());
        account.setAuthUrl(CLIENT.authUrl());
        account.setUserAgent(CLIENT.userAgent());

        lenient().when(credentialsService.requireCurrentUserId()).thenReturn(USER_ID);
        lenient().when(credentialsService.requireUserById(USER_ID)).thenReturn(new AppUser());
        lenient().when(accountService.requireOwnedAccount(ACCOUNT_ID)).thenReturn(account);
        lenient().when(accountService.requireOwnedAccountForUser(ACCOUNT_ID, USER_ID)).thenReturn(account);
        lenient().when(accountService.toClientCredentials(account)).thenReturn(CLIENT);
        lenient().when(accountService.listConnectedAccounts())
                .thenAnswer(invocation -> storedToken.get() == null ? List.of() : List.of(account));
        lenient().when(tokenRepository.existsById(ACCOUNT_ID))
                .thenAnswer(invocation -> storedToken.get() != null);
        lenient().when(tokenRepository.save(any(AllegroToken.class))).thenAnswer(invocation -> {
            AllegroToken token = invocation.getArgument(0);
            storedToken.set(token);
            return token;
        });
        lenient().when(tokenRepository.findById(ACCOUNT_ID))
                .thenAnswer(invocation -> Optional.ofNullable(storedToken.get()));

        clock = Clock.fixed(Instant.parse("2026-01-15T12:00:00Z"), ZoneOffset.UTC);
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        authService = new AllegroAuthService(
                PROPERTIES,
                tokenRepository,
                accountService,
                credentialsService,
                builder.build(),
                MapperFixtures.tokenMapper(),
                clock);
    }

    @Test
    void shouldExchangeAuthorizationCodeAndSaveToken() {
        String basicAuth = Base64.getEncoder().encodeToString("client-id:client-secret".getBytes());

        server.expect(requestTo(startsWith(AUTH_URL + "/auth/oauth/token")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Basic " + basicAuth))
                .andExpect(header(HttpHeaders.USER_AGENT, USER_AGENT))
                .andExpect(content().contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andRespond(withSuccess("""
                        {
                          "access_token": "access-1",
                          "token_type": "bearer",
                          "refresh_token": "refresh-1",
                          "expires_in": 3600,
                          "scope": "allegro:api:sale:offers:read",
                          "allegro_api": true
                        }
                        """, MediaType.APPLICATION_JSON));

        authService.exchangeAuthorizationCode("auth-code", AllegroAuthService.encodeState(USER_ID, ACCOUNT_ID));

        assertThat(authService.isConnected()).isTrue();
        assertThat(authService.getValidAccessToken(ACCOUNT_ID)).isEqualTo("access-1");
        assertThat(storedToken.get().getAccountId()).isEqualTo(ACCOUNT_ID);
        server.verify();
    }

    @Test
    void shouldRefreshExpiredToken() {
        AllegroToken token = new AllegroToken();
        token.setAccountId(ACCOUNT_ID);
        token.setAccessToken("old-access");
        token.setRefreshToken("refresh-1");
        token.setExpiresAt(Instant.parse("2026-01-15T11:00:00Z"));
        token.setUpdatedAt(Instant.parse("2026-01-15T10:00:00Z"));
        storedToken.set(token);

        server.expect(requestTo(startsWith(AUTH_URL + "/auth/oauth/token")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.USER_AGENT, USER_AGENT))
                .andRespond(withSuccess("""
                        {
                          "access_token": "new-access",
                          "token_type": "bearer",
                          "refresh_token": "refresh-2",
                          "expires_in": 3600,
                          "scope": "allegro:api:sale:offers:read",
                          "allegro_api": true
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThat(authService.getValidAccessToken(ACCOUNT_ID)).isEqualTo("new-access");
        assertThat(storedToken.get().getRefreshToken()).isEqualTo("refresh-2");
        server.verify();
    }

    @Test
    void shouldBuildAuthorizationUrlWithState() {
        String url = authService.buildAuthorizationUrl(ACCOUNT_ID);

        assertThat(url).startsWith(AUTH_URL + "/auth/oauth/authorize?");
        assertThat(url).contains("client_id=client-id");
        assertThat(url).contains("redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Fapi%2Fallegro%2Fauth%2Fcallback");
        assertThat(url).contains("scope=allegro%3Aapi%3Asale%3Aoffers%3Aread%20allegro%3Aapi%3Aorders%3Aread");
        assertThat(url).contains("state=42%3A7");
        assertThat(authService.resolveRedirectUri())
                .isEqualTo("http://localhost:8080/api/allegro/auth/callback");
    }

    @Test
    void shouldOmitScopeFromAuthorizationUrlWhenScopesBlank() {
        AllegroAuthService withoutScopes = new AllegroAuthService(
                new AllegroApiProperties("http://localhost:8080/api/allegro/auth/callback", "  "),
                tokenRepository,
                accountService,
                credentialsService,
                RestClient.builder().build(),
                MapperFixtures.tokenMapper(),
                clock);

        String url = withoutScopes.buildAuthorizationUrl(ACCOUNT_ID);

        assertThat(url).doesNotContain("scope=");
        assertThat(url).contains("client_id=client-id");
        assertThat(url).contains("state=42%3A7");
    }

    @Test
    void shouldDescribeAuthorizationErrorWithAccountContext() {
        String message = authService.describeAuthorizationError(
                "unauthorized_client",
                "OAuth 2.0 Parameter: client_id",
                AllegroAuthService.encodeState(USER_ID, ACCOUNT_ID));

        assertThat(message).contains("Allegro odrzuciło autoryzację");
        assertThat(message).contains("client_id");
        assertThat(message).contains("produkcja vs sandbox");
    }

    @Test
    void shouldTruncateLongBodiesForLog() {
        assertThat(AllegroAuthService.truncateForLog(null)).isEmpty();
        assertThat(AllegroAuthService.truncateForLog("short")).isEqualTo("short");
        assertThat(AllegroAuthService.truncateForLog("x".repeat(600))).endsWith("…").hasSize(501);
    }
}
