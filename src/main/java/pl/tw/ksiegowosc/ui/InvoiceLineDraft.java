package pl.tw.ksiegowosc.ui;

import java.math.BigDecimal;
import java.math.RoundingMode;

import pl.tw.ksiegowosc.dto.CreateInvoiceLineRequest;
import pl.tw.ksiegowosc.dto.MeritTaxDto;
import pl.tw.ksiegowosc.dto.MeritUnitDto;

/**
 * Editable invoice line held in the create-invoice UI before submit.
 */
public class InvoiceLineDraft {

    private String itemCode;
    private String description;
    private Integer itemType;
    private BigDecimal quantity;
    private BigDecimal price;
    private BigDecimal taxAmount;
    private MeritTaxDto tax;
    private MeritUnitDto uom;

    public String getItemCode() {
        return itemCode;
    }

    public void setItemCode(String itemCode) {
        this.itemCode = itemCode;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getItemType() {
        return itemType;
    }

    public void setItemType(Integer itemType) {
        this.itemType = itemType;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public void setTaxAmount(BigDecimal taxAmount) {
        this.taxAmount = taxAmount;
    }

    public MeritTaxDto getTax() {
        return tax;
    }

    public void setTax(MeritTaxDto tax) {
        this.tax = tax;
    }

    public MeritUnitDto getUom() {
        return uom;
    }

    public void setUom(MeritUnitDto uom) {
        this.uom = uom;
    }

    public String taxId() {
        return tax == null ? null : tax.id();
    }

    public String uomName() {
        if (uom == null || uom.name() == null || uom.name().isBlank()) {
            return null;
        }
        return uom.name().trim();
    }

    public String taxLabel() {
        if (tax == null || tax.taxPct() == null) {
            return "";
        }
        return tax.taxPct().stripTrailingZeros().toPlainString() + "%";
    }

    public CreateInvoiceLineRequest toRequest() {
        return new CreateInvoiceLineRequest(
                itemCode,
                description,
                itemType,
                quantity,
                price,
                taxId(),
                uomName());
    }

    /** Podgląd VAT: round(price × qty, 2) × stawka%. */
    static BigDecimal vatFromPrice(BigDecimal price, BigDecimal quantity, BigDecimal taxPct) {
        if (price == null || quantity == null || taxPct == null) {
            return null;
        }
        BigDecimal lineNet = price.multiply(quantity).setScale(2, RoundingMode.HALF_UP);
        return lineNet.multiply(taxPct).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }
}
