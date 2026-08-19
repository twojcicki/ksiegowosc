package pl.tw.ksiegowosc.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SalesInvoiceHeaderDto(
        @JsonProperty("SIHId") String sihId,
        @JsonProperty("DepartmentCode") String departmentCode,
        @JsonProperty("DepartmentName") String departmentName,
        @JsonProperty("ProjectCode") String projectCode,
        @JsonProperty("ProjectName") String projectName,
        @JsonProperty("BatchInfo") String batchInfo,
        @JsonProperty("InvoiceNo") String invoiceNo,
        @JsonProperty("DocumentDate") String documentDate,
        @JsonProperty("TransactionDate") String transactionDate,
        @JsonProperty("CustomerName") String customerName,
        @JsonProperty("HComment") String headerComment,
        @JsonProperty("FComment") String footerComment,
        @JsonProperty("DueDate") String dueDate,
        @JsonProperty("CurrencyCode") String currencyCode,
        @JsonProperty("CurrencyRate") BigDecimal currencyRate,
        @JsonProperty("TaxAmount") BigDecimal taxAmount,
        @JsonProperty("RoundingAmount") BigDecimal roundingAmount,
        @JsonProperty("TotalAmount") BigDecimal totalAmount,
        @JsonProperty("ProfitAmount") BigDecimal profitAmount,
        @JsonProperty("TotalSum") BigDecimal totalSum,
        @JsonProperty("UserName") String userName,
        @JsonProperty("ReferenceNo") String referenceNo,
        @JsonProperty("PriceInclVat") Boolean priceInclVat,
        @JsonProperty("VatRegNo") String vatRegNo,
        @JsonProperty("OfferId") String offerId,
        @JsonProperty("OfferDocType") Integer offerDocType,
        @JsonProperty("OfferNo") String offerNo,
        @JsonProperty("FileName") String fileName,
        @JsonProperty("FileContent") String fileContent,
        @JsonProperty("PerSHId") String perShId,
        @JsonProperty("ContractNo") String contractNo
) {
}
