package pl.tw.ksiegowosc.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
class AppUserDetailsServiceTest {

    @Autowired
    private AppUserDetailsService appUserDetailsService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void loadsSeededAdminUser() {
        UserDetails admin = appUserDetailsService.loadUserByUsername("admin");
        assertThat(admin.getUsername()).isEqualTo("admin");
        assertThat(passwordEncoder.matches("admin", admin.getPassword())).isTrue();
        assertThat(admin.getAuthorities()).extracting(Object::toString).contains("ROLE_USER");
    }

    @Test
    void unknownLoginThrows() {
        assertThatThrownBy(() -> appUserDetailsService.loadUserByUsername("nobody"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
