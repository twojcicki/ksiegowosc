package pl.tw.ksiegowosc.dto.allegro;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AllegroBuyer(
        String login,
        String email,
        String firstName,
        String lastName,
        AllegroBuyerAddress address) {
}
