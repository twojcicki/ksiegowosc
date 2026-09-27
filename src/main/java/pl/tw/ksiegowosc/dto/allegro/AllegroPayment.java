package pl.tw.ksiegowosc.dto.allegro;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AllegroPayment(
        String id,
        String type,
        String provider,
        Instant finishedAt,
        AllegroPrice paidAmount) {
}
