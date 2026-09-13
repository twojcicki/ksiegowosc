package pl.tw.ksiegowosc.controller;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import pl.tw.ksiegowosc.dto.AllegroAuthStatusDto;
import pl.tw.ksiegowosc.service.AllegroAuthService;

@RestController
@RequestMapping("/api/allegro/auth")
@Tag(name = "Allegro — autoryzacja", description = "OAuth2 z Allegro Sandbox")
public class AllegroAuthController {

    private final AllegroAuthService authService;

    public AllegroAuthController(AllegroAuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/connect")
    @Operation(summary = "Rozpocznij autoryzację OAuth", description = "Przekierowuje do Allegro Sandbox w celu połączenia konta sprzedawcy.")
    public void connect(HttpServletResponse response) throws IOException {
        response.sendRedirect(authService.buildAuthorizationUrl());
    }

    @GetMapping("/callback")
    @Operation(summary = "Callback OAuth", description = "Odbiera kod autoryzacyjny i zapisuje token w bazie.")
    public void callback(
            @RequestParam String code,
            @RequestParam String state,
            HttpServletResponse response) throws IOException {
        authService.exchangeAuthorizationCode(code, state);
        response.sendRedirect("/allegro");
    }

    @PostMapping("/disconnect")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Usuń powiązanie Allegro", description = "Kasuje tokeny OAuth bieżącego użytkownika.")
    public void disconnect() {
        authService.disconnect();
    }

    @GetMapping("/status")
    @Operation(summary = "Status połączenia z Allegro")
    public AllegroAuthStatusDto status() {
        return new AllegroAuthStatusDto(authService.isConnected());
    }
}
