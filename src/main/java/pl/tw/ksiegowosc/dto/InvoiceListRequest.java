package pl.tw.ksiegowosc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record InvoiceListRequest(
        @JsonProperty("PeriodStart") String periodStart,
        @JsonProperty("PeriodEnd") String periodEnd,
        @JsonProperty("DateType") int dateType
) {
}
