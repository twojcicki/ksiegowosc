package pl.tw.ksiegowosc.dto;

public record AllegroAccountDto(
        Long id,
        String name,
        String clientId,
        String invoicePrefix,
        String paymentMethod,
        String apiBaseUrl,
        String authUrl,
        String userAgent,
        boolean clientSecretSet,
        boolean connected
) {
}
