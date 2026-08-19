package pl.tw.ksiegowosc.client;

import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.ObjectMapper;

import pl.tw.ksiegowosc.dto.MeritErrorResponse;

public final class MeritErrorMessages {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private MeritErrorMessages() {
    }

    public static String from(RestClientResponseException ex) {
        String body = ex.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            return defaultMessage(ex);
        }
        try {
            MeritErrorResponse parsed = OBJECT_MAPPER.readValue(body, MeritErrorResponse.class);
            if (parsed.message() != null && !parsed.message().isBlank()) {
                return parsed.message();
            }
        } catch (Exception ignored) {
            // body is not JSON with Message
        }
        return body.trim();
    }

    private static String defaultMessage(RestClientResponseException ex) {
        String statusText = ex.getStatusText();
        return statusText == null || statusText.isBlank()
                ? "Żądanie do API Merit nie powiodło się."
                : statusText;
    }
}
