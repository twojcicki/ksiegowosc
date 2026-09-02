package pl.tw.ksiegowosc.client;

import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.ObjectMapper;

import pl.tw.ksiegowosc.dto.allegro.AllegroError;
import pl.tw.ksiegowosc.dto.allegro.AllegroErrorResponse;

public final class AllegroErrorMessages {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private AllegroErrorMessages() {
    }

    public static String from(RestClientResponseException ex) {
        String body = ex.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            return defaultMessage(ex);
        }
        try {
            AllegroErrorResponse parsed = OBJECT_MAPPER.readValue(body, AllegroErrorResponse.class);
            if (parsed.errors() != null && !parsed.errors().isEmpty()) {
                AllegroError first = parsed.errors().getFirst();
                if (first.userMessage() != null && !first.userMessage().isBlank()) {
                    return first.userMessage();
                }
                if (first.message() != null && !first.message().isBlank()) {
                    return first.message();
                }
            }
        } catch (Exception ignored) {
            // body is not Allegro error JSON
        }
        return body.trim();
    }

    private static String defaultMessage(RestClientResponseException ex) {
        String statusText = ex.getStatusText();
        return statusText == null || statusText.isBlank()
                ? "Żądanie do API Allegro nie powiodło się."
                : statusText;
    }
}
