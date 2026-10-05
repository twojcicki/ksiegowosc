package pl.tw.ksiegowosc.security;

import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Runs work as a given app user login for background jobs that depend on SecurityContext
 * (Merit credentials, Allegro account scoping).
 */
public final class RunAsUser {

    private RunAsUser() {
    }

    public static void run(String login, Runnable action) {
        if (login == null || login.isBlank()) {
            throw new IllegalArgumentException("login is required");
        }
        SecurityContext previous = SecurityContextHolder.getContext();
        try {
            UserDetails principal = User.withUsername(login.trim())
                    .password("N/A")
                    .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                    .build();
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            action.run();
        } finally {
            SecurityContextHolder.clearContext();
            if (previous != null) {
                SecurityContextHolder.setContext(previous);
            }
        }
    }
}
