package pl.tw.ksiegowosc.dto;

public record UserApiSettingsDto(
        String meritApiId,
        boolean meritApiKeySet
) {
}
