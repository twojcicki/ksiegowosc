package pl.tw.ksiegowosc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AllegroTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("expires_in") long expiresIn,
        String scope,
        @JsonProperty("allegro_api") Boolean allegroApi) {
}
