package pl.tw.ksiegowosc.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "clients.merit")
public record MeritApiProperties(
        @NotBlank String baseUrl,
        @NotBlank String apiId,
        @NotBlank String apiKey,
        @NotBlank String v2BaseUrl
) {
}
