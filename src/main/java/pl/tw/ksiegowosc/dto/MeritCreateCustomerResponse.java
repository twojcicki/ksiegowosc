package pl.tw.ksiegowosc.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MeritCreateCustomerResponse(
        @JsonProperty("Id") String id,
        @JsonProperty("Name") String name
) {
}
