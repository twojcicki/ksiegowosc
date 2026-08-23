package pl.tw.ksiegowosc.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CustomerDto(
        @JsonProperty("CustomerId") String customerId,
        @JsonProperty("Name") String name,
        @JsonProperty("RegNo") String regNo,
        @JsonProperty("VatRegNo") String vatRegNo,
        @JsonProperty("Email") String email,
        @JsonProperty("City") String city,
        @JsonProperty("Address") String address,
        @JsonProperty("CurrencyCode") String currencyCode
) {
}
