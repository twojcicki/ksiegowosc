package pl.tw.ksiegowosc.dto;

public record AllegroAccountDto(
        Long id,
        String name,
        String clientId,
        String invoicePrefix,
        boolean clientSecretSet,
        boolean connected
) {
}
