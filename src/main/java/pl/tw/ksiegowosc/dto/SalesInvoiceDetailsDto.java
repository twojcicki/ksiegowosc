package pl.tw.ksiegowosc.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SalesInvoiceDetailsDto(
        @JsonProperty("Header") SalesInvoiceHeaderDto header,
        @JsonProperty("Lines") List<SalesInvoiceLineDto> lines,
        @JsonProperty("Payments") List<SalesInvoicePaymentDto> payments,
        @JsonProperty("Allocations") Object allocations
) {
}
