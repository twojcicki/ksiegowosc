package pl.tw.ksiegowosc.ui;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.ListDataProvider;

import pl.tw.ksiegowosc.client.MeritErrorMessages;
import pl.tw.ksiegowosc.dto.CreateInvoiceLineRequest;
import pl.tw.ksiegowosc.dto.CreateInvoiceRequest;
import pl.tw.ksiegowosc.dto.CreateInvoiceResponse;
import pl.tw.ksiegowosc.dto.CreateInvoiceTaxAmountRequest;
import pl.tw.ksiegowosc.dto.MeritTaxDto;
import pl.tw.ksiegowosc.dto.MeritUnitDto;
import pl.tw.ksiegowosc.service.InvoicesService;
import pl.tw.ksiegowosc.service.TaxesService;
import pl.tw.ksiegowosc.service.UnitsService;
import pl.tw.ksiegowosc.ui.util.Lucide;

public class CreateInvoiceDialog extends Dialog {

    private static final Locale PL = Locale.forLanguageTag("pl-PL");

    private final InvoicesService invoicesService;
    private final TaxesService taxesService;
    private final UnitsService unitsService;
    private final Runnable onSuccess;
    private final NumberFormat amountFormat;

    private final TextField customerId = new TextField("Klient (customerId)");
    private final TextField invoiceNo = new TextField("Numer faktury");
    private final DatePicker docDate = new DatePicker("Data dokumentu");
    private final DatePicker dueDate = new DatePicker("Termin płatności");
    private final TextField currencyCode = new TextField("Waluta");
    private final TextArea headerComment = new TextArea("Komentarz górny");
    private final TextArea footerComment = new TextArea("Komentarz dolny");
    private final NumberField totalAmount = new NumberField("Kwota netto");
    private final NumberField totalTaxAmount = new NumberField("Suma VAT");

    private final List<InvoiceLineDraft> lines = new ArrayList<>();
    private final ListDataProvider<InvoiceLineDraft> linesProvider = new ListDataProvider<>(lines);
    private final Grid<InvoiceLineDraft> linesGrid = new Grid<>(InvoiceLineDraft.class, false);

    private List<MeritTaxDto> taxes = List.of();
    private List<MeritUnitDto> units = List.of();
    private MeritUnitDto defaultUnit;

    private final Button saveButton = new Button("Zapisz");

    public CreateInvoiceDialog(
            InvoicesService invoicesService,
            TaxesService taxesService,
            UnitsService unitsService,
            Runnable onSuccess) {
        this.invoicesService = invoicesService;
        this.taxesService = taxesService;
        this.unitsService = unitsService;
        this.onSuccess = onSuccess;
        this.amountFormat = NumberFormat.getNumberInstance(PL);
        this.amountFormat.setMinimumFractionDigits(2);
        this.amountFormat.setMaximumFractionDigits(2);

        setHeaderTitle("Nowa faktura");
        setWidth("800px");
        setMaxWidth("95vw");
        setCloseOnEsc(true);
        setCloseOnOutsideClick(false);

        configureFields();
        loadTaxes();
        loadUnits();
        configureLinesGrid();

        FormLayout headerForm = new FormLayout(
                customerId,
                invoiceNo,
                docDate,
                dueDate,
                currencyCode,
                totalAmount,
                totalTaxAmount,
                headerComment,
                footerComment);
        headerForm.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("480px", 2));
        headerForm.setColspan(headerComment, 2);
        headerForm.setColspan(footerComment, 2);

        Button addLineButton = new Button("Dodaj pozycję", Lucide.PLUS.create(), e -> openLineDialog(null));
        addLineButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout linesHeader = new HorizontalLayout(new H3("Pozycje"), addLineButton);
        linesHeader.setWidthFull();
        linesHeader.setAlignItems(FlexComponent.Alignment.CENTER);
        linesHeader.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        VerticalLayout content = new VerticalLayout(headerForm, linesHeader, linesGrid);
        content.setPadding(false);
        content.setSpacing(true);
        content.setWidthFull();

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

        refreshTotals();
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

        totalAmount.setReadOnly(true);
        totalAmount.setHelperText("Suma kwot netto pozycji");
        totalTaxAmount.setReadOnly(true);
        totalTaxAmount.setHelperText("Suma VAT z pozycji");

        headerComment.setWidthFull();
        footerComment.setWidthFull();
    }

    private void configureLinesGrid() {
        linesGrid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.NO_BORDER);
        linesGrid.setDataProvider(linesProvider);
        linesGrid.setAllRowsVisible(true);
        linesGrid.setWidthFull();

        linesGrid.addColumn(InvoiceLineDraft::getItemCode).setHeader("Kod").setAutoWidth(true).setFlexGrow(0);
        linesGrid.addColumn(InvoiceLineDraft::getDescription).setHeader("Opis").setFlexGrow(1);
        linesGrid
                .addColumn(line -> formatAmount(line.getQuantity()))
                .setHeader("Ilość")
                .setAutoWidth(true)
                .setFlexGrow(0);
        linesGrid
                .addColumn(line -> formatAmount(line.getPrice()))
                .setHeader("Cena")
                .setAutoWidth(true)
                .setFlexGrow(0);
        linesGrid
                .addColumn(line -> formatAmount(line.getLineNet()))
                .setHeader("Netto")
                .setAutoWidth(true)
                .setFlexGrow(0);
        linesGrid.addColumn(InvoiceLineDraft::taxLabel).setHeader("VAT").setAutoWidth(true).setFlexGrow(0);
        linesGrid
                .addColumn(line -> line.uomName() == null ? "" : line.uomName())
                .setHeader("JM")
                .setAutoWidth(true)
                .setFlexGrow(0);
        linesGrid
                .addComponentColumn(line -> {
                    Button edit = new Button("Edytuj", e -> openLineDialog(line));
                    edit.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
                    Button remove = new Button("Usuń", e -> removeLine(line));
                    remove.addThemeVariants(
                            ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
                    HorizontalLayout actions = new HorizontalLayout(edit, remove);
                    actions.setSpacing(true);
                    return actions;
                })
                .setHeader("Akcje")
                .setAutoWidth(true)
                .setFlexGrow(0);
    }

    private void openLineDialog(InvoiceLineDraft existing) {
        InvoiceLineDialog dialog = new InvoiceLineDialog(
                taxes,
                units,
                defaultUnit,
                existing,
                draft -> {
                    if (existing == null) {
                        lines.add(draft);
                    }
                    linesProvider.refreshAll();
                    refreshTotals();
                });
        dialog.open();
    }

    private void removeLine(InvoiceLineDraft line) {
        lines.remove(line);
        linesProvider.refreshAll();
        refreshTotals();
    }

    private void refreshTotals() {
        BigDecimal netSum = BigDecimal.ZERO;
        BigDecimal vatSum = BigDecimal.ZERO;
        for (InvoiceLineDraft line : lines) {
            if (line.getLineNet() != null) {
                netSum = netSum.add(line.getLineNet());
            }
            if (line.getTaxAmount() != null) {
                vatSum = vatSum.add(line.getTaxAmount());
            }
        }
        if (lines.isEmpty()) {
            totalAmount.clear();
            totalTaxAmount.clear();
        } else {
            totalAmount.setValue(netSum.doubleValue());
            totalTaxAmount.setValue(vatSum.doubleValue());
        }
    }

    private void loadUnits() {
        try {
            units = unitsService.listUnits();
            if (!units.isEmpty()) {
                defaultUnit = unitsService.requireDefaultUnit(units);
            }
        } catch (RuntimeException ex) {
            units = List.of();
            showError("Nie udało się pobrać jednostek miary z Merit.");
        }
    }

    private void loadTaxes() {
        try {
            taxes = taxesService.listTaxes();
        } catch (RuntimeException ex) {
            taxes = List.of();
            showError("Nie udało się pobrać stawek VAT z Merit.");
        }
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
                || isBlank(footerComment.getValue())) {
            showError("Uzupełnij wszystkie wymagane pola.");
            return null;
        }
        if (lines.isEmpty()) {
            showError("Dodaj co najmniej jedną pozycję.");
            return null;
        }

        BigDecimal totalNet = BigDecimal.ZERO;
        Map<String, BigDecimal> vatByTaxId = new LinkedHashMap<>();
        List<CreateInvoiceLineRequest> lineRequests = new ArrayList<>();

        for (InvoiceLineDraft line : lines) {
            if (line.getLineNet() == null
                    || line.getTaxAmount() == null
                    || isBlank(line.taxId())
                    || line.getQuantity() == null
                    || line.getPrice() == null) {
                showError("Pozycje faktury są niekompletne.");
                return null;
            }
            totalNet = totalNet.add(line.getLineNet());
            vatByTaxId.merge(line.taxId().trim(), line.getTaxAmount(), BigDecimal::add);
            lineRequests.add(line.toRequest());
        }

        List<CreateInvoiceTaxAmountRequest> taxAmounts = vatByTaxId.entrySet().stream()
                .map(entry -> new CreateInvoiceTaxAmountRequest(entry.getKey(), entry.getValue()))
                .toList();

        return new CreateInvoiceRequest(
                customerId.getValue().trim(),
                invoiceNo.getValue().trim(),
                docDate.getValue(),
                dueDate.getValue(),
                currencyCode.getValue().trim(),
                headerComment.getValue().trim(),
                footerComment.getValue().trim(),
                totalNet,
                lineRequests,
                taxAmounts);
    }

    private String formatAmount(BigDecimal value) {
        if (value == null) {
            return "";
        }
        return amountFormat.format(value);
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
