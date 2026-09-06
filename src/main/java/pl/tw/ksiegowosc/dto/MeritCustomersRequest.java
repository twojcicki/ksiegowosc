package pl.tw.ksiegowosc.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MeritCustomersRequest(
        @JsonProperty("Name") String name,
        @JsonProperty("VatRegNo") String vatRegNo
) {
    public MeritCustomersRequest(String name) {
        this(name, null);
    }
}
