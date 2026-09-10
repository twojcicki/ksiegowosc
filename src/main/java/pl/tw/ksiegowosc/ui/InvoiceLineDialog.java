package pl.tw.ksiegowosc.ui;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Consumer;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;

import pl.tw.ksiegowosc.dto.MeritTaxDto;
import pl.tw.ksiegowosc.dto.MeritUnitDto;
import pl.tw.ksiegowosc.service.TaxesService;

public class InvoiceLineDialog extends Dialog {

    private final List<MeritTaxDto> taxes;
    private final List<MeritUnitDto> units;
    private final MeritUnitDto defaultUnit;
    private final Consumer<InvoiceLineDraft> onSave;
    private final InvoiceLineDraft editing;

    private final TextField itemCode = new TextField("Kod pozycji");
    private final TextField description = new TextField("Opis");
    private final Select<Integer> itemType = new Select<>();
    private final ComboBox<MeritUnitDto> uom = new ComboBox<>("Jednostka miary");
    private final NumberField lineNet = new NumberField("Kwota netto pozycji");
    private final NumberField quantity = new NumberField("Ilość");
    private final NumberField price = new NumberField("Cena netto");
    private final ComboBox<MeritTaxDto> taxRate = new ComboBox<>("Stawka VAT");
    private final NumberField taxAmount = new NumberField("Kwota VAT");

    public InvoiceLineDialog(
            List<MeritTaxDto> taxes,
            List<MeritUnitDto> units,
            MeritUnitDto defaultUnit,
            InvoiceLineDraft editing,
            Consumer<InvoiceLineDraft> onSave) {
        this.taxes = taxes == null ? List.of() : taxes;
        this.units = units == null ? List.of() : units;
        this.defaultUnit = defaultUnit;
        this.editing = editing;
        this.onSave = onSave;

        setHeaderTitle(editing == null ? "Dodaj pozycję" : "Edytuj pozycję");
        setWidth("560px");
        setMaxWidth("95vw");
        setCloseOnEsc(true);
        setCloseOnOutsideClick(false);

        configureFields();
        if (editing != null) {
            populate(editing);
        }

        FormLayout form = new FormLayout(
                itemCode, description, itemType, uom, lineNet, quantity, price, taxRate, taxAmount);
        form.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("480px", 2));
        form.setColspan(description, 2);
        add(form);

        Button cancelButton = new Button("Anuluj", event -> close());
        Button saveButton = new Button("Zapisz pozycję", event -> save());
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout footer = new HorizontalLayout(cancelButton, saveButton);
        footer.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        footer.setWidthFull();
        getFooter().add(footer);
    }

    private void configureFields() {
        itemCode.setRequiredIndicatorVisible(true);
        description.setRequiredIndicatorVisible(true);

        itemType.setLabel("Typ pozycji");
        itemType.setItems(1, 2, 3);
        itemType.setItemLabelGenerator(type -> switch (type) {
            case 1 -> "1 — towar magazynowy";
            case 2 -> "2 — usługa";
            case 3 -> "3 — pozycja";
            default -> String.valueOf(type);
        });
        itemType.setValue(1);
        itemType.setRequiredIndicatorVisible(true);
        itemType.addValueChangeListener(e -> updateUomRequirement());

        uom.setItems(this.units);
        uom.setItemLabelGenerator(this::formatUnit);
        uom.setWidthFull();
        uom.setHelperText("Jednostki z Merit (Ustawienia → Jednostki miary)");
        if (defaultUnit != null) {
            uom.setValue(defaultUnit);
        } else if (!this.units.isEmpty()) {
            uom.setValue(this.units.getFirst());
        }
        updateUomRequirement();

        lineNet.setRequiredIndicatorVisible(true);
        lineNet.setMin(0);

        quantity.setRequiredIndicatorVisible(true);
        quantity.setValue(1.0);
        quantity.setMin(0.000001);

        price.setReadOnly(true);
        price.setHelperText("Wyliczana z kwoty netto / ilość");

        taxRate.setItems(this.taxes);
        taxRate.setRequiredIndicatorVisible(true);
        taxRate.setItemLabelGenerator(this::formatTax);
        taxRate.setWidthFull();
        this.taxes.stream()
                .filter(tax -> tax.taxPct() != null
                        && tax.taxPct().compareTo(TaxesService.FALLBACK_VAT_PERCENT) == 0)
                .findFirst()
                .ifPresentOrElse(taxRate::setValue, () -> {
                    if (!this.taxes.isEmpty()) {
                        taxRate.setValue(this.taxes.getFirst());
                    }
                });

        taxAmount.setReadOnly(true);
        taxAmount.setHelperText("Wyliczana z kwoty netto × stawka VAT");

        lineNet.addValueChangeListener(e -> recalculateDerivedAmounts());
        quantity.addValueChangeListener(e -> recalculateDerivedAmounts());
        taxRate.addValueChangeListener(e -> recalculateDerivedAmounts());
        recalculateDerivedAmounts();
    }

    private void populate(InvoiceLineDraft draft) {
        itemCode.setValue(nullToEmpty(draft.getItemCode()));
        description.setValue(nullToEmpty(draft.getDescription()));
        if (draft.getItemType() != null) {
            itemType.setValue(draft.getItemType());
        }
        if (draft.getUom() != null) {
            uom.setValue(draft.getUom());
        }
        if (draft.getLineNet() != null) {
            lineNet.setValue(draft.getLineNet().doubleValue());
        }
        if (draft.getQuantity() != null) {
            quantity.setValue(draft.getQuantity().doubleValue());
        }
        if (draft.getTax() != null) {
            taxRate.setValue(draft.getTax());
        }
        updateUomRequirement();
        recalculateDerivedAmounts();
    }

    private void updateUomRequirement() {
        boolean stockItem = Integer.valueOf(1).equals(itemType.getValue());
        uom.setRequiredIndicatorVisible(stockItem);
    }

    private void recalculateDerivedAmounts() {
        Double netValue = lineNet.getValue();
        Double qtyValue = quantity.getValue();
        MeritTaxDto tax = taxRate.getValue();

        if (netValue == null || qtyValue == null || qtyValue <= 0) {
            price.clear();
        } else {
            BigDecimal unitNet = InvoiceLineDraft.unitPriceFromLineNet(
                    BigDecimal.valueOf(netValue), BigDecimal.valueOf(qtyValue));
            price.setValue(unitNet.doubleValue());
        }

        if (netValue == null || tax == null || tax.taxPct() == null) {
            taxAmount.clear();
            return;
        }
        BigDecimal vat = InvoiceLineDraft.vatFromLineNet(BigDecimal.valueOf(netValue), tax.taxPct());
        taxAmount.setValue(vat.doubleValue());
    }

    private void save() {
        if (isBlank(itemCode.getValue())
                || isBlank(description.getValue())
                || itemType.getValue() == null
                || (Integer.valueOf(1).equals(itemType.getValue())
                        && (uom.getValue() == null || isBlank(uom.getValue().name())))
                || lineNet.getValue() == null
                || quantity.getValue() == null
                || price.getValue() == null
                || taxRate.getValue() == null
                || isBlank(taxRate.getValue().id())
                || taxAmount.getValue() == null) {
            showError("Uzupełnij wszystkie wymagane pola pozycji.");
            return;
        }

        InvoiceLineDraft draft = editing == null ? new InvoiceLineDraft() : editing;
        draft.setItemCode(itemCode.getValue().trim());
        draft.setDescription(description.getValue().trim());
        draft.setItemType(itemType.getValue());
        draft.setQuantity(BigDecimal.valueOf(quantity.getValue()));
        draft.setPrice(BigDecimal.valueOf(price.getValue()));
        draft.setLineNet(BigDecimal.valueOf(lineNet.getValue()));
        draft.setTaxAmount(BigDecimal.valueOf(taxAmount.getValue()));
        draft.setTax(taxRate.getValue());
        draft.setUom(uom.getValue());

        onSave.accept(draft);
        close();
    }

    private String formatUnit(MeritUnitDto unit) {
        if (unit == null) {
            return "";
        }
        String name = unit.name() == null || unit.name().isBlank() ? "?" : unit.name();
        if (unit.code() == null || unit.code().isBlank() || unit.code().equals(name)) {
            return name;
        }
        return unit.code() + " — " + name;
    }

    private String formatTax(MeritTaxDto tax) {
        if (tax == null) {
            return "";
        }
        String pct = tax.taxPct() == null ? "?" : tax.taxPct().stripTrailingZeros().toPlainString();
        String code = tax.code() == null || tax.code().isBlank() ? "" : tax.code() + " — ";
        String name = tax.name() == null || tax.name().isBlank() ? "VAT" : tax.name();
        return code + name + " (" + pct + "%)";
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static void showError(String message) {
        Notification notification = Notification.show(message, 5000, Notification.Position.TOP_END);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
}
