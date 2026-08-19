package pl.tw.ksiegowosc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SendInvoiceEmailRequest(
        @JsonProperty("Id") String id,
        @JsonProperty("DelivNote") boolean delivNote
) {
}
