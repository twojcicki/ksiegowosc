package pl.tw.ksiegowosc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MeritCreateInvoiceItem(
        @JsonProperty("Code") String code,
        @JsonProperty("Description") String description,
        @JsonProperty("Type") int type
) {
}
