package pl.tw.ksiegowosc.dto;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SalesInvoiceLineDto(
        @JsonProperty("ArticleCode") String articleCode,
        @JsonProperty("LocationCode") String locationCode,
        @JsonProperty("Quantity") BigDecimal quantity,
        @JsonProperty("Price") BigDecimal price,
        @JsonProperty("TaxName") String taxName,
        @JsonProperty("TaxPct") BigDecimal taxPct,
        @JsonProperty("AmountExclVat") BigDecimal amountExclVat,
        @JsonProperty("AmountInclVat") BigDecimal amountInclVat,
        @JsonProperty("VatAmount") BigDecimal vatAmount,
        @JsonProperty("AccountCode") String accountCode,
        @JsonProperty("DepartmentCode") String departmentCode,
        @JsonProperty("DepartmentName") String departmentName,
        @JsonProperty("ItemCostAmount") BigDecimal itemCostAmount,
        @JsonProperty("ProfitAmount") BigDecimal profitAmount,
        @JsonProperty("DiscountPct") BigDecimal discountPct,
        @JsonProperty("DiscountAmount")
        @JsonAlias("DicountAmount")
        BigDecimal discountAmount,
        @JsonProperty("Description") String description,
        @JsonProperty("UOMName") String uomName,
        @JsonProperty("FixAsset") Boolean fixAsset,
        @JsonProperty("ProjectAllocation") List<CostAllocationDto> projectAllocation,
        @JsonProperty("CostCenterAllocation") List<CostAllocationDto> costCenterAllocation
) {
}
