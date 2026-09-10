package pl.tw.ksiegowosc.ui;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;

import pl.tw.ksiegowosc.client.MeritErrorMessages;
import pl.tw.ksiegowosc.dto.CreateInvoiceLineRequest;
import pl.tw.ksiegowosc.dto.CreateInvoiceRequest;
import pl.tw.ksiegowosc.dto.CreateInvoiceResponse;
import pl.tw.ksiegowosc.dto.CreateInvoiceTaxAmountRequest;
import pl.tw.ksiegowosc.dto.MeritTaxDto;
import pl.tw.ksiegowosc.service.InvoicesService;
import pl.tw.ksiegowosc.service.TaxesService;

public class CreateInvoiceDialog extends Dialog {

    private static final Locale PL = Locale.forLanguageTag("pl-PL");

    private final InvoicesService invoicesService;
    private final TaxesService taxesService;
    private final Runnable onSuccess;

    private final TextField customerId = new TextField("Klient (customerId)");
    private final TextField invoiceNo = new TextField("Numer faktury");
    private final DatePicker docDate = new DatePicker("Data dokumentu");
    private final DatePicker dueDate = new DatePicker("Termin płatności");
    private final TextField currencyCode = new TextField("Waluta");
    private final TextArea headerComment = new TextArea("Komentarz górny");
    private final TextArea footerComment = new TextArea("Komentarz dolny");
    private final NumberField totalAmount = new NumberField("Kwota netto");

    private final TextField itemCode = new TextField("Kod pozycji");
    private final TextField description = new TextField("Opis");
    private final Select<Integer> itemType = new Select<>();
    private final NumberField quantity = new NumberField("Ilość");
    private final NumberField price = new NumberField("Cena");
    private final ComboBox<MeritTaxDto> taxRate = new ComboBox<>("Stawka VAT");
    private final NumberField taxAmount = new NumberField("Kwota VAT");

    private final Button saveButton = new Button("Zapisz");

    public CreateInvoiceDialog(InvoicesService invoicesService, TaxesService taxesService, Runnable onSuccess) {
        this.invoicesService = invoicesService;
        this.taxesService = taxesService;
        this.onSuccess = onSuccess;

        setHeaderTitle("Nowa faktura");
        setWidth("640px");
        setMaxWidth("95vw");
        setCloseOnEsc(true);
        setCloseOnOutsideClick(false);

        configureFields();
        loadTaxes();

        FormLayout headerForm = new FormLayout(
                customerId,
                invoiceNo,
                docDate,
                dueDate,
                currencyCode,
                totalAmount,
                headerComment,
                footerComment);
        headerForm.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("480px", 2));
        headerForm.setColspan(headerComment, 2);
        headerForm.setColspan(footerComment, 2);

        FormLayout lineForm = new FormLayout(itemCode, description, itemType, quantity, price, taxRate);
        lineForm.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("480px", 2));

        FormLayout taxForm = new FormLayout(taxAmount);
        taxForm.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

        VerticalLayout content = new VerticalLayout(
                headerForm,
                new H3("Pozycja"),
                lineForm,
                new H3("VAT"),
                taxForm);
        content.setPadding(false);
        content.setSpacing(true);

        Scroller scroller = new Scroller(content);
        scroller.setMaxHeight("70vh");
        scroller.setWidthFull();
        add(scroller);

        Button cancelButton = new Button("Anuluj", event -> close());
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveButton.addClickListener(event -> save());

        HorizontalLayout footer = new HorizontalLayout(cancelButton, saveButton);
        footer.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        footer.setWidthFull();
        getFooter().add(footer);
    }

    private void configureFields() {
        LocalDate today = LocalDate.now();
        docDate.setLocale(PL);
        dueDate.setLocale(PL);
        docDate.setValue(today);
        dueDate.setValue(today.plusDays(14));
        currencyCode.setValue("PLN");

        customerId.setValue("6fb6b812-08ed-4bed-5e80-08def950532f");
        customerId.setRequiredIndicatorVisible(true);
        invoiceNo.setRequiredIndicatorVisible(true);
        invoiceNo.setMaxLength(35);
        docDate.setRequiredIndicatorVisible(true);
        dueDate.setRequiredIndicatorVisible(true);
        currencyCode.setRequiredIndicatorVisible(true);
        headerComment.setRequiredIndicatorVisible(true);
        footerComment.setRequiredIndicatorVisible(true);
        totalAmount.setRequiredIndicatorVisible(true);
        totalAmount.setMin(0);

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
        quantity.setRequiredIndicatorVisible(true);
        quantity.setValue(1.0);
        quantity.setMin(0.000001);
        price.setLabel("Cena netto");
        price.setReadOnly(true);
        price.setHelperText("Wyliczana z kwoty netto / ilość");
        taxRate.setRequiredIndicatorVisible(true);
        taxRate.setItemLabelGenerator(this::formatTax);
        taxRate.setWidthFull();
        taxAmount.setReadOnly(true);
        taxAmount.setHelperText("Wyliczana z kwoty netto × stawka VAT");

        totalAmount.addValueChangeListener(e -> recalculateDerivedAmounts());
        quantity.addValueChangeListener(e -> recalculateDerivedAmounts());
        taxRate.addValueChangeListener(e -> recalculateDerivedAmounts());

        headerComment.setWidthFull();
        footerComment.setWidthFull();
    }

    private void recalculateDerivedAmounts() {
        Double netValue = totalAmount.getValue();
        Double qtyValue = quantity.getValue();
        MeritTaxDto tax = taxRate.getValue();

        if (netValue == null || qtyValue == null || qtyValue <= 0) {
            price.clear();
        } else {
            BigDecimal unitNet = BigDecimal.valueOf(netValue)
                    .divide(BigDecimal.valueOf(qtyValue), 2, RoundingMode.HALF_UP);
            price.setValue(unitNet.doubleValue());
        }

        if (netValue == null || tax == null || tax.taxPct() == null) {
            taxAmount.clear();
            return;
        }
        BigDecimal vat = BigDecimal.valueOf(netValue)
                .multiply(tax.taxPct())
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        taxAmount.setValue(vat.doubleValue());
    }

    private void loadTaxes() {
        try {
            List<MeritTaxDto> taxes = taxesService.listTaxes();
            taxRate.setItems(taxes);
            taxes.stream()
                    .filter(tax -> tax.taxPct() != null
                            && tax.taxPct().compareTo(TaxesService.FALLBACK_VAT_PERCENT) == 0)
                    .findFirst()
                    .ifPresentOrElse(taxRate::setValue, () -> {
                        if (!taxes.isEmpty()) {
                            taxRate.setValue(taxes.getFirst());
                        }
                    });
            recalculateDerivedAmounts();
        } catch (RuntimeException ex) {
            showError("Nie udało się pobrać stawek VAT z Merit.");
        }
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

    private void save() {
        CreateInvoiceRequest request = buildRequest();
        if (request == null) {
            return;
        }

        saveButton.setEnabled(false);
        try {
            CreateInvoiceResponse response = invoicesService.createInvoice(request);
            showSuccess("Utworzono fakturę " + request.invoiceNo()
                    + (response.invoiceId() == null ? "." : " (" + response.invoiceId() + ")."));
            onSuccess.run();
            close();
        } catch (ResponseStatusException ex) {
            showError(reason(ex));
        } catch (RestClientResponseException ex) {
            showError(MeritErrorMessages.from(ex));
        } catch (RuntimeException ex) {
            showError("Nie udało się utworzyć faktury.");
        } finally {
            saveButton.setEnabled(true);
        }
    }

    private CreateInvoiceRequest buildRequest() {
        if (isBlank(customerId.getValue())
                || isBlank(invoiceNo.getValue())
                || docDate.getValue() == null
                || dueDate.getValue() == null
                || isBlank(currencyCode.getValue())
                || isBlank(headerComment.getValue())
                || isBlank(footerComment.getValue())
                || totalAmount.getValue() == null
                || isBlank(itemCode.getValue())
                || isBlank(description.getValue())
                || itemType.getValue() == null
                || quantity.getValue() == null
                || price.getValue() == null
                || taxRate.getValue() == null
                || isBlank(taxRate.getValue().id())
                || taxAmount.getValue() == null) {
            showError("Uzupełnij wszystkie wymagane pola.");
            return null;
        }

        String taxIdValue = taxRate.getValue().id().trim();
        return new CreateInvoiceRequest(
                customerId.getValue().trim(),
                invoiceNo.getValue().trim(),
                docDate.getValue(),
                dueDate.getValue(),
                currencyCode.getValue().trim(),
                headerComment.getValue().trim(),
                footerComment.getValue().trim(),
                BigDecimal.valueOf(totalAmount.getValue()),
                List.of(new CreateInvoiceLineRequest(
                        itemCode.getValue().trim(),
                        description.getValue().trim(),
                        itemType.getValue(),
                        BigDecimal.valueOf(quantity.getValue()),
                        BigDecimal.valueOf(price.getValue()),
                        taxIdValue)),
                List.of(new CreateInvoiceTaxAmountRequest(
                        taxIdValue,
                        BigDecimal.valueOf(taxAmount.getValue()))));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String reason(ResponseStatusException ex) {
        if (ex.getReason() == null || ex.getReason().isBlank()) {
            return "Nie udało się utworzyć faktury.";
        }
        return ex.getReason();
    }

    private static void showSuccess(String message) {
        Notification notification = Notification.show(message, 5000, Notification.Position.TOP_END);
        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    private static void showError(String message) {
        Notification notification = Notification.show(message, 5000, Notification.Position.TOP_END);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
}
