package pl.tw.ksiegowosc.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CostAllocationDto(
        @JsonProperty("Code") String code,
        @JsonProperty("AllocPct") BigDecimal allocPct,
        @JsonProperty("AllocAmount") BigDecimal allocAmount
) {
}
