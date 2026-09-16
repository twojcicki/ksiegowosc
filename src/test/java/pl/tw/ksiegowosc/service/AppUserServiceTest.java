package pl.tw.ksiegowosc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import pl.tw.ksiegowosc.entity.AppUser;
import pl.tw.ksiegowosc.repository.AppUserRepository;

class AppUserServiceTest {

    private CurrentUserApiCredentialsService currentUserService;
    private AppUserRepository appUserRepository;
    private PasswordEncoder passwordEncoder;
    private AppUserService service;
    private AppUser user;

    @BeforeEach
    void setUp() {
        currentUserService = mock(CurrentUserApiCredentialsService.class);
        appUserRepository = mock(AppUserRepository.class);
        passwordEncoder = new BCryptPasswordEncoder();
        service = new AppUserService(currentUserService, appUserRepository, passwordEncoder);

        user = new AppUser();
        user.setId(1L);
        user.setLogin("admin");
        user.setPasswordHash(passwordEncoder.encode("old-password"));
        when(currentUserService.requireCurrentUser()).thenReturn(user);
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void shouldChangePasswordWhenCurrentMatches() {
        service.changePassword("old-password", "new-password", "new-password");

        assertThat(passwordEncoder.matches("new-password", user.getPasswordHash())).isTrue();
        verify(appUserRepository).save(user);
    }

    @Test
    void shouldRejectWrongCurrentPassword() {
        assertThatThrownBy(() -> service.changePassword("wrong", "new-password", "new-password"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).contains("Obecne hasło");
                });
        verify(appUserRepository, never()).save(any());
    }

    @Test
    void shouldRejectConfirmationMismatch() {
        assertThatThrownBy(() -> service.changePassword("old-password", "new-password", "other-password"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getReason()).contains("potwierdzenie"));
        verify(appUserRepository, never()).save(any());
    }

    @Test
    void shouldRejectTooShortPassword() {
        assertThatThrownBy(() -> service.changePassword("old-password", "short", "short"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getReason()).contains("8"));
        verify(appUserRepository, never()).save(any());
    }

    @Test
    void shouldRejectSameAsCurrent() {
        assertThatThrownBy(() -> service.changePassword("old-password", "old-password", "old-password"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getReason()).contains("różnić"));
        verify(appUserRepository, never()).save(any());
    }
}
