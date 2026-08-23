package pl.tw.ksiegowosc.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MeritCreateInvoiceResponse(
        @JsonProperty("InvoiceId") String invoiceId,
        @JsonProperty("CustomerId") String customerId
) {
}
