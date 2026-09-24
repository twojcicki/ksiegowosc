package pl.tw.ksiegowosc.controller;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import pl.tw.ksiegowosc.dto.AllegroAuthStatusDto;
import pl.tw.ksiegowosc.service.AllegroAuthService;

@RestController
@RequestMapping("/api/allegro/auth")
@Tag(name = "Allegro — autoryzacja", description = "OAuth2 z Allegro")
public class AllegroAuthController {

    private final AllegroAuthService authService;

    public AllegroAuthController(AllegroAuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/connect")
    @Operation(summary = "Rozpocznij autoryzację OAuth", description = "Przekierowuje do Allegro w celu połączenia wybranego konta.")
    public void connect(@RequestParam Long accountId, HttpServletResponse response) throws IOException {
        response.sendRedirect(authService.buildAuthorizationUrl(accountId));
    }

    @GetMapping("/callback")
    @Operation(summary = "Callback OAuth", description = "Odbiera kod autoryzacyjny albo błąd OAuth i wraca do Ustawień API.")
    public void callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            @RequestParam(required = false, name = "error_description") String errorDescription,
            HttpServletResponse response) throws IOException {
        if (StringUtils.hasText(error)) {
            String message = authService.describeAuthorizationError(error, errorDescription, state);
            response.sendRedirect("/ustawienia-api?allegro_oauth_error="
                    + URLEncoder.encode(message, StandardCharsets.UTF_8));
            return;
        }
        if (!StringUtils.hasText(code) || !StringUtils.hasText(state)) {
            String message = "Brak parametru code/state w callbacku Allegro.";
            response.sendRedirect("/ustawienia-api?allegro_oauth_error="
                    + URLEncoder.encode(message, StandardCharsets.UTF_8));
            return;
        }
        authService.exchangeAuthorizationCode(code, state);
        response.sendRedirect("/ustawienia-api");
    }

    @GetMapping("/status")
    @Operation(summary = "Status — czy użytkownik ma jakiekolwiek połączone konto Allegro")
    public AllegroAuthStatusDto status() {
        return new AllegroAuthStatusDto(authService.isConnected());
    }
}
