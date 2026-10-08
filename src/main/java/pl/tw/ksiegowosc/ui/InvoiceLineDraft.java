package pl.tw.ksiegowosc.ui;

import java.math.BigDecimal;
import java.math.RoundingMode;

import pl.tw.ksiegowosc.dto.CreateInvoiceLineRequest;
import pl.tw.ksiegowosc.dto.MeritTaxDto;
import pl.tw.ksiegowosc.dto.MeritUnitDto;
import pl.tw.ksiegowosc.mapper.AllegroInvoiceMappingSupport;

/**
 * Editable invoice line held in the create-invoice UI before submit.
 */
public class InvoiceLineDraft {

    private String itemCode;
    private String description;
    private Integer itemType;
    private BigDecimal quantity;
    private BigDecimal price;
    private BigDecimal lineGross;
    private BigDecimal lineNet;
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

    public BigDecimal getLineGross() {
        return lineGross;
    }

    public void setLineGross(BigDecimal lineGross) {
        this.lineGross = lineGross;
    }

    public BigDecimal getLineNet() {
        return lineNet;
    }

    public void setLineNet(BigDecimal lineNet) {
        this.lineNet = lineNet;
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

    /**
     * Jak Allegro: unitGross = brutto/qty → Price = toNet(..., 7 dp); lineNet = Price×qty.
     */
    static DerivedAmounts deriveFromGross(BigDecimal lineGross, BigDecimal quantity, BigDecimal taxPct) {
        if (lineGross == null || quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0 || taxPct == null) {
            return null;
        }
        BigDecimal vatRate = AllegroInvoiceMappingSupport.vatRateFromPercent(taxPct);
        BigDecimal unitGross = lineGross.divide(
                quantity, AllegroInvoiceMappingSupport.UNIT_NET_SCALE, RoundingMode.HALF_UP);
        BigDecimal unitNet = AllegroInvoiceMappingSupport.toNet(
                unitGross, vatRate, AllegroInvoiceMappingSupport.UNIT_NET_SCALE);
        BigDecimal lineNet = unitNet.multiply(quantity)
                .setScale(AllegroInvoiceMappingSupport.UNIT_NET_SCALE, RoundingMode.HALF_UP);
        BigDecimal lineVat = lineGross
                .setScale(2, RoundingMode.HALF_UP)
                .subtract(lineNet.setScale(2, RoundingMode.HALF_UP));
        return new DerivedAmounts(unitNet, lineNet, lineVat);
    }

    record DerivedAmounts(BigDecimal unitNet, BigDecimal lineNet, BigDecimal lineVat) {
    }
}
