package pl.tw.ksiegowosc.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "clients.allegro")
public record AllegroApiProperties(
        @NotBlank String apiBaseUrl,
        @NotBlank String authUrl,
        /** Empty = derive from current request (needed on Render without env). */
        String redirectUri,
        @NotBlank String scopes
) {
}
