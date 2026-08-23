package pl.tw.ksiegowosc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MeritCreateInvoiceCustomer(
        @JsonProperty("Id") String id
) {
}
