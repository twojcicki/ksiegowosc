package pl.tw.ksiegowosc.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MeritPaymentDto(
        @JsonProperty("PIHId") String pihId,
        @JsonProperty("DocumentNo") String documentNo,
        @JsonProperty("DocumentDate") String documentDate,
        @JsonProperty("PaymAPIDetails") MeritPaymApiDetails paymApiDetails
) {
}
