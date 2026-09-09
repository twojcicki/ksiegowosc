package pl.tw.ksiegowosc.security;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import pl.tw.ksiegowosc.entity.AppUser;
import pl.tw.ksiegowosc.repository.AppUserRepository;

@Component
public class AppUserSeedRunner implements ApplicationRunner {

    static final String DEFAULT_LOGIN = "admin";
    static final String DEFAULT_PASSWORD = "admin";

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    public AppUserSeedRunner(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (appUserRepository.findByLogin(DEFAULT_LOGIN).isPresent()) {
            return;
        }
        AppUser admin = new AppUser();
        admin.setLogin(DEFAULT_LOGIN);
        admin.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
        appUserRepository.save(admin);
    }
}
