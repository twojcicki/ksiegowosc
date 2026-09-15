package pl.tw.ksiegowosc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.server.ResponseStatusException;

import pl.tw.ksiegowosc.dto.MeritCredentials;
import pl.tw.ksiegowosc.dto.UserApiSettingsDto;
import pl.tw.ksiegowosc.entity.AppUser;
import pl.tw.ksiegowosc.entity.UserApiCredentials;
import pl.tw.ksiegowosc.repository.AppUserRepository;
import pl.tw.ksiegowosc.repository.UserApiCredentialsRepository;

class CurrentUserApiCredentialsServiceTest {

    private AppUserRepository appUserRepository;
    private UserApiCredentialsRepository credentialsRepository;
    private CurrentUserApiCredentialsService service;

    @BeforeEach
    void setUp() {
        appUserRepository = mock(AppUserRepository.class);
        credentialsRepository = mock(UserApiCredentialsRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-09-13T12:00:00Z"), ZoneOffset.UTC);
        service = new CurrentUserApiCredentialsService(appUserRepository, credentialsRepository, clock);

        AppUser user = new AppUser();
        user.setId(7L);
        user.setLogin("admin");
        when(appUserRepository.findByLogin("admin")).thenReturn(Optional.of(user));

        var userDetails = User.withUsername("admin").password("x").roles("USER").build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, "x", userDetails.getAuthorities()));
    }

    @Test
    void shouldSaveMeritCredentialsAndRequireThem() {
        when(credentialsRepository.findById(7L)).thenReturn(Optional.empty());
        when(credentialsRepository.save(any(UserApiCredentials.class))).thenAnswer(inv -> inv.getArgument(0));

        service.saveMeritCredentials("merit-id", "merit-key");

        verify(credentialsRepository).save(any(UserApiCredentials.class));

        UserApiCredentials stored = new UserApiCredentials();
        stored.setUserId(7L);
        stored.setMeritApiId("merit-id");
        stored.setMeritApiKey("merit-key");
        when(credentialsRepository.findById(7L)).thenReturn(Optional.of(stored));

        MeritCredentials credentials = service.requireMeritCredentials();
        assertThat(credentials.apiId()).isEqualTo("merit-id");
        assertThat(credentials.apiKey()).isEqualTo("merit-key");
    }

    @Test
    void shouldKeepExistingSecretWhenBlankOnUpdate() {
        UserApiCredentials existing = new UserApiCredentials();
        existing.setUserId(7L);
        existing.setMeritApiId("old-id");
        existing.setMeritApiKey("old-key");
        when(credentialsRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(credentialsRepository.save(any(UserApiCredentials.class))).thenAnswer(inv -> inv.getArgument(0));

        service.saveMeritCredentials("new-id", "  ");

        assertThat(existing.getMeritApiId()).isEqualTo("new-id");
        assertThat(existing.getMeritApiKey()).isEqualTo("old-key");
    }

    @Test
    void shouldRejectMissingMeritCredentials() {
        when(credentialsRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireMeritCredentials())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Ustawieniach API");
    }

    @Test
    void shouldReturnSettingsWithoutExposingSecrets() {
        UserApiCredentials existing = new UserApiCredentials();
        existing.setUserId(7L);
        existing.setMeritApiId("merit-id");
        existing.setMeritApiKey("secret");
        when(credentialsRepository.findById(7L)).thenReturn(Optional.of(existing));

        UserApiSettingsDto settings = service.getSettings();

        assertThat(settings.meritApiId()).isEqualTo("merit-id");
        assertThat(settings.meritApiKeySet()).isTrue();
    }
}
