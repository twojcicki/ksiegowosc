package pl.tw.ksiegowosc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "clients.allegro")
public record AllegroApiProperties(
        /** Empty = derive from current request (needed on Render without env). */
        String redirectUri,
        /**
         * Space-separated OAuth scopes for the authorize URL.
         * Empty = do not send {@code scope} (Allegro uses scopes declared for the app).
         */
        String scopes
) {
}
