package pl.tw.ksiegowosc.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MeritErrorResponse(
        @JsonProperty("Message") String message,
        @JsonProperty("detail") String detail,
        @JsonProperty("code") String code
) {
}
