package pl.tw.ksiegowosc.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MeritTaxDto(
        @JsonProperty("Id") String id,
        @JsonProperty("Code") String code,
        @JsonProperty("Name") String name,
        @JsonProperty("TaxPct") @JsonAlias("Percent") BigDecimal taxPct) {
}
