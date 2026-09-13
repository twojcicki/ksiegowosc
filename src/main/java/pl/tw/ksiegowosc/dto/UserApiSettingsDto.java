package pl.tw.ksiegowosc.dto;

public record UserApiSettingsDto(
        String meritApiId,
        boolean meritApiKeySet,
        String allegroClientId,
        boolean allegroClientSecretSet,
        boolean allegroConnected
) {
}
