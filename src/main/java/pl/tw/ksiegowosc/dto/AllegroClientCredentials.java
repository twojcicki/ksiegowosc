package pl.tw.ksiegowosc.dto;

public record AllegroClientCredentials(
        String clientId,
        String clientSecret,
        String apiBaseUrl,
        String authUrl,
        String userAgent
) {
}
