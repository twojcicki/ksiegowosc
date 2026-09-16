package pl.tw.ksiegowosc.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import pl.tw.ksiegowosc.entity.AppUser;
import pl.tw.ksiegowosc.repository.AppUserRepository;

@Service
public class AppUserService {

    static final int MIN_PASSWORD_LENGTH = 8;

    private final CurrentUserApiCredentialsService currentUserService;
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    public AppUserService(
            CurrentUserApiCredentialsService currentUserService,
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder) {
        this.currentUserService = currentUserService;
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void changePassword(String currentPassword, String newPassword, String confirmPassword) {
        if (!StringUtils.hasText(currentPassword)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Podaj obecne hasło.");
        }
        if (!StringUtils.hasText(newPassword)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Podaj nowe hasło.");
        }
        if (!StringUtils.hasText(confirmPassword)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Potwierdź nowe hasło.");
        }
        if (newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Nowe hasło musi mieć co najmniej " + MIN_PASSWORD_LENGTH + " znaków.");
        }
        if (!newPassword.equals(confirmPassword)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nowe hasło i potwierdzenie muszą być takie same.");
        }
        if (currentPassword.equals(newPassword)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nowe hasło musi różnić się od obecnego.");
        }

        AppUser user = currentUserService.requireCurrentUser();
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Obecne hasło jest nieprawidłowe.");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        appUserRepository.save(user);
    }
}
