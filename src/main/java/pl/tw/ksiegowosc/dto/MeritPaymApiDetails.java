package pl.tw.ksiegowosc.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MeritPaymApiDetails(
        @JsonProperty("PaymId") String paymId,
        @JsonProperty("DocNo") String docNo,
        @JsonProperty("DocId") String docId
) {
}
