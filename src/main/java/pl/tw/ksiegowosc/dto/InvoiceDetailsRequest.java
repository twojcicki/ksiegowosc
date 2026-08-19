package pl.tw.ksiegowosc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record InvoiceDetailsRequest(
        @JsonProperty("Id") String id,
        @JsonProperty("AddAttachment") boolean addAttachment
) {
}
