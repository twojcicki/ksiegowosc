package pl.tw.ksiegowosc.dto;

public record AllegroAccountDto(
        Long id,
        String name,
        String clientId,
        boolean clientSecretSet,
        boolean connected
) {
}
