package pl.tw.ksiegowosc.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MeritUnitDto(
        @JsonProperty("Code") String code,
        @JsonProperty("Name") String name) {
}
