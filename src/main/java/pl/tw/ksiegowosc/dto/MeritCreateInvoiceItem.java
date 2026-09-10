package pl.tw.ksiegowosc.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MeritCreateInvoiceItem(
        @JsonProperty("Code") String code,
        @JsonProperty("Description") String description,
        @JsonProperty("Type") int type,
        @JsonProperty("UOMName") String uomName
) {
}
