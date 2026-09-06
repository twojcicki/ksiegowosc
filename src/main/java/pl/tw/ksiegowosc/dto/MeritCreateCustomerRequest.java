package pl.tw.ksiegowosc.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MeritCreateCustomerRequest(
        @JsonProperty("Name") String name,
        @JsonProperty("NotTDCustomer") Boolean notTdCustomer,
        @JsonProperty("CountryCode") String countryCode,
        @JsonProperty("VatRegNo") String vatRegNo,
        @JsonProperty("Address") String address,
        @JsonProperty("City") String city,
        @JsonProperty("PostalCode") String postalCode,
        @JsonProperty("Email") String email,
        @JsonProperty("CurrencyCode") String currencyCode,
        @JsonProperty("SalesInvLang") String salesInvLang
) {
}
