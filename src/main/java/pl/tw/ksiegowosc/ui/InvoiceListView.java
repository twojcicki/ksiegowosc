package pl.tw.ksiegowosc.ui;

import java.text.NumberFormat;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.PermitAll;
import pl.tw.ksiegowosc.client.MeritErrorMessages;
import pl.tw.ksiegowosc.dto.BulkDeleteInvoiceFailure;
import pl.tw.ksiegowosc.dto.BulkDeleteInvoiceResult;
import pl.tw.ksiegowosc.dto.DeleteInvoiceResult;
import pl.tw.ksiegowosc.dto.SalesInvoiceDetailsDto;
import pl.tw.ksiegowosc.dto.SalesInvoiceDto;
import pl.tw.ksiegowosc.dto.SalesInvoiceHeaderDto;
import pl.tw.ksiegowosc.dto.SalesInvoiceLineDto;
import pl.tw.ksiegowosc.service.CustomersService;
import pl.tw.ksiegowosc.service.InvoicesService;
import pl.tw.ksiegowosc.service.TaxesService;
import pl.tw.ksiegowosc.service.UnitsService;
import pl.tw.ksiegowosc.ui.component.View;
import pl.tw.ksiegowosc.ui.component.ViewHeader;
import pl.tw.ksiegowosc.ui.util.Aura;
import pl.tw.ksiegowosc.ui.util.Lucide;
import pl.tw.ksiegowosc.ui.util.Notifications;

@Route("")
@PageTitle("Faktury")
@PermitAll
public class InvoiceListView extends View {

    private static final Locale PL = Locale.forLanguageTag("pl-PL");
    private static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");
    private static final DateTimeFormatter EMAIL_SENT_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", PL);

    private final InvoicesService invoicesService;
    private final CustomersService customersService;
    private final TaxesService taxesService;
    private final UnitsService unitsService;
    private final DatePicker fromPicker = new DatePicker("Od");
    private final DatePicker toPicker = new DatePicker("Do");
    private final Button deleteSelectedButton = new Button("Usuń zaznaczone");
    private final Grid<SalesInvoiceDto> grid = new Grid<>(SalesInvoiceDto.class, false);
    private final NumberFormat amountFormat;

    public InvoiceListView(
            InvoicesService invoicesService,
            CustomersService customersService,
            TaxesService taxesService,
            UnitsService unitsService) {
        this.invoicesService = invoicesService;
        this.customersService = customersService;
        this.taxesService = taxesService;
        this.unitsService = unitsService;
        this.amountFormat = NumberFormat.getNumberInstance(PL);
        this.amountFormat.setMinimumFractionDigits(2);
        this.amountFormat.setMaximumFractionDigits(2);

        addClassNames(Aura.SURFACE_SOLID, "invoices-view");
        add(createHeader(), createFilters(), createGrid());
        loadInvoices();
    }

    private ViewHeader createHeader() {
        DrawerToggle toggle = new DrawerToggle();
        toggle.addThemeVariants(ButtonVariant.TERTIARY);

        H1 title = new H1("Faktury");

        Button addInvoice = new Button("Dodaj fakturę", Lucide.PLUS.create(), e -> openCreateInvoiceDialog());
        addInvoice.addThemeVariants(ButtonVariant.PRIMARY);

        return new ViewHeader(toggle, title, addInvoice);
    }

    private HorizontalLayout createFilters() {
        fromPicker.setLocale(PL);
        toPicker.setLocale(PL);
        LocalDate today = LocalDate.now();
        fromPicker.setValue(today.minusDays(30));
        toPicker.setValue(today);

        Button search = new Button("Szukaj", e -> loadInvoices());
        search.addThemeVariants(ButtonVariant.PRIMARY);

        deleteSelectedButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
        deleteSelectedButton.setEnabled(false);
        deleteSelectedButton.addClickListener(e -> confirmDeleteSelected());

        HorizontalLayout filters = new HorizontalLayout(fromPicker, toPicker, search, deleteSelectedButton);
        filters.addClassName("filters");
        filters.setWidthFull();
        return filters;
    }

    private Grid<SalesInvoiceDto> createGrid() {
        grid.setSelectionMode(Grid.SelectionMode.MULTI);
        grid.addSelectionListener(event -> updateDeleteSelectedButton());
        grid.addThemeVariants(GridVariant.NO_BORDER);
        grid.addColumn(SalesInvoiceDto::invoiceNo).setHeader("Numer").setAutoWidth(true).setSortable(true);
        grid.addColumn(SalesInvoiceDto::documentDate).setHeader("Data").setAutoWidth(true).setSortable(true);
        grid.addColumn(SalesInvoiceDto::customerName).setHeader("Klient").setFlexGrow(1).setSortable(true);
        grid.addColumn(invoice -> formatAmount(invoice.totalAmount()))
                .setHeader("Netto")
                .setAutoWidth(true);
        grid.addColumn(invoice -> formatAmount(invoice.taxAmount()))
                .setHeader("VAT")
                .setAutoWidth(true);
        grid.addColumn(invoice -> formatAmount(invoice.roundingAmount()))
                .setHeader("Zaokr.")
                .setAutoWidth(true);
        grid.addColumn(invoice -> formatAmount(invoice.profitAmount()))
                .setHeader("Marża")
                .setAutoWidth(true);
        grid.addColumn(invoice -> formatAmount(invoice.totalSum()))
                .setHeader("Brutto")
                .setAutoWidth(true);
        grid.addColumn(invoice -> Boolean.TRUE.equals(invoice.paid()) ? "Tak" : "Nie")
                .setHeader("Zapłacono")
                .setAutoWidth(true)
                .setSortable(true);
        grid.addColumn(invoice -> Boolean.TRUE.equals(invoice.emailSent()) ? "Tak" : "Nie")
                .setHeader("Wysłano")
                .setAutoWidth(true)
                .setSortable(true);
        grid.addColumn(invoice -> formatEmailSentAt(invoice.emailSentAt()))
                .setHeader("Data wysyłki")
                .setAutoWidth(true)
                .setSortable(true);
        grid.addComponentColumn(invoice -> {
            Button details = new Button(Lucide.SQUARE_PEN.create(), e -> openDetailsDialog(invoice));
            details.addThemeVariants(ButtonVariant.TERTIARY);
            details.setAriaLabel("Szczegóły");
            details.setTooltipText("Szczegóły");

            Button email = new Button("E-mail");
            email.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
            email.setEnabled(invoice.sihId() != null && !invoice.sihId().isBlank());
            email.addClickListener(e -> confirmSendEmail(invoice, email));

            Button delete = new Button("Usuń");
            delete.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
            delete.setEnabled(invoice.sihId() != null && !invoice.sihId().isBlank());
            delete.addClickListener(e -> confirmDeleteInvoice(invoice, delete));

            return new HorizontalLayout(details, email, delete);
        }).setHeader("Akcje").setAutoWidth(true).setFlexGrow(0).setFrozenToEnd(true);
        grid.setSizeFull();
        return grid;
    }

    private void openCreateInvoiceDialog() {
        CreateInvoiceDialog dialog = new CreateInvoiceDialog(
                invoicesService, customersService, taxesService, unitsService, this::loadInvoices);
        dialog.open();
    }

    private void openDetailsDialog(SalesInvoiceDto invoice) {
        if (invoice.sihId() == null || invoice.sihId().isBlank()) {
            showError("Brak identyfikatora faktury.");
            return;
        }
        try {
            SalesInvoiceDetailsDto details = invoicesService.getInvoiceDetails(invoice.sihId(), false);
            String title = invoice.invoiceNo() == null || invoice.invoiceNo().isBlank()
                    ? invoice.sihId()
                    : invoice.invoiceNo();

            Div content = new Div();
            content.addClassName("dialog-content");
            SalesInvoiceHeaderDto header = details.header();
            if (header != null) {
                String currency = header.currencyCode() == null ? "" : " " + header.currencyCode();
                content.add(detailRow("Klient", header.customerName()));
                content.add(detailRow("Data dokumentu", header.documentDate()));
                content.add(detailRow("Termin płatności", header.dueDate()));
                content.add(detailRow("Netto", formatAmountWithCurrency(header.totalAmount(), currency)));
                content.add(detailRow("VAT", formatAmountWithCurrency(header.taxAmount(), currency)));
                content.add(detailRow("Zaokrąglenie", formatAmountWithCurrency(header.roundingAmount(), currency)));
                content.add(detailRow("Marża", formatAmountWithCurrency(header.profitAmount(), currency)));
                content.add(detailRow("Brutto", formatAmountWithCurrency(header.totalSum(), currency)));
                content.add(detailRow("NIP", header.vatRegNo()));
                content.add(detailRow("Komentarz", header.headerComment()));
            } else {
                content.add(detailRow("Klient", invoice.customerName()));
            }

            List<SalesInvoiceLineDto> lines = details.lines();
            if (lines != null && !lines.isEmpty()) {
                Div linesBox = new Div();
                linesBox.addClassName("dialog-lines");
                linesBox.add(new Span("Pozycje"));
                for (SalesInvoiceLineDto line : lines) {
                    String desc = line.description() == null || line.description().isBlank()
                            ? line.articleCode()
                            : line.description();
                    String amount = line.amountInclVat() == null ? "" : amountFormat.format(line.amountInclVat());
                    linesBox.add(detailRow(desc, amount));
                }
                content.add(linesBox);
            }

            Dialog dialog = new Dialog(content);
            dialog.addClassName("invoice-details-dialog");
            dialog.setHeaderTitle(title);
            dialog.setWidth("480px");
            Button close = new Button("Zamknij", e -> dialog.close());
            close.addThemeVariants(ButtonVariant.TERTIARY);
            dialog.getFooter().add(close);
            dialog.open();
        } catch (ResponseStatusException ex) {
            showError(reason(ex));
        } catch (RestClientResponseException ex) {
            showError(MeritErrorMessages.from(ex));
        } catch (RuntimeException ex) {
            showError("Nie udało się pobrać szczegółów faktury.");
        }
    }

    private Div detailRow(String label, String value) {
        Span labelSpan = new Span(label == null ? "" : label);
        labelSpan.getStyle().set("color", "var(--vaadin-text-color-secondary)");
        labelSpan.getStyle().set("font-size", "var(--vaadin-font-size-s)");
        Span valueSpan = new Span(value == null || value.isBlank() ? "—" : value);
        Div row = new Div(labelSpan, valueSpan);
        row.getStyle().set("display", "flex");
        row.getStyle().set("flex-direction", "column");
        row.getStyle().set("gap", "2px");
        return row;
    }

    private void confirmSendEmail(SalesInvoiceDto invoice, Button emailButton) {
        String invoiceNo = invoice.invoiceNo() == null || invoice.invoiceNo().isBlank()
                ? invoice.sihId()
                : invoice.invoiceNo();
        String customer = invoice.customerName() == null || invoice.customerName().isBlank()
                ? "—"
                : invoice.customerName();

        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Wyślij fakturę e-mailem?");
        dialog.setText("Faktura " + invoiceNo + " / " + customer);
        dialog.setCancelable(true);
        dialog.setCancelText("Anuluj");
        dialog.setConfirmText("Wyślij");
        dialog.addConfirmListener(event -> {
            dialog.close();
            sendInvoiceByEmail(invoice, emailButton);
        });
        dialog.open();
    }

    private void confirmDeleteInvoice(SalesInvoiceDto invoice, Button deleteButton) {
        String invoiceNo = invoiceLabel(invoice);

        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Usuń fakturę");
        dialog.setText("Czy na pewno chcesz usunąć fakturę o numerze " + invoiceNo + "?");
        dialog.setCancelable(true);
        dialog.setCancelText("Nie");
        dialog.setConfirmText("Tak");
        dialog.setConfirmButtonTheme("error primary");
        dialog.addConfirmListener(event -> {
            dialog.close();
            deleteInvoice(invoice, deleteButton);
        });
        dialog.open();
    }

    private void confirmDeleteSelected() {
        List<SalesInvoiceDto> selected = selectedDeletableInvoices();
        if (selected.isEmpty()) {
            showError("Zaznacz co najmniej jedną fakturę.");
            return;
        }
        List<String> numbers = selected.stream().map(InvoiceListView::invoiceLabel).toList();
        int count = numbers.size();

        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Usuń zaznaczone faktury");
        dialog.setText("Czy na pewno chcesz usunąć " + count + " faktur: " + String.join(", ", numbers) + "?");
        dialog.setCancelable(true);
        dialog.setCancelText("Nie");
        dialog.setConfirmText("Tak");
        dialog.setConfirmButtonTheme("error primary");
        dialog.addConfirmListener(event -> {
            dialog.close();
            deleteSelectedInvoices(selected);
        });
        dialog.open();
    }

    private void deleteSelectedInvoices(List<SalesInvoiceDto> selected) {
        deleteSelectedButton.setEnabled(false);
        List<String> ids = selected.stream()
                .map(SalesInvoiceDto::sihId)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(id -> !id.isBlank())
                .toList();
        try {
            BulkDeleteInvoiceResult result = invoicesService.deleteInvoices(ids);
            showBulkDeleteResult(result);
            grid.deselectAll();
            loadInvoices();
        } catch (ResponseStatusException ex) {
            showError(reason(ex));
        } catch (RuntimeException ex) {
            showError("Nie udało się usunąć zaznaczonych faktur.");
        } finally {
            updateDeleteSelectedButton();
        }
    }

    private void showBulkDeleteResult(BulkDeleteInvoiceResult result) {
        int ok = result.successes() == null ? 0 : result.successes().size();
        int failed = result.failures() == null ? 0 : result.failures().size();
        if (failed == 0) {
            String numbers = result.successes().stream()
                    .map(DeleteInvoiceResult::invoiceNo)
                    .filter(Objects::nonNull)
                    .filter(no -> !no.isBlank())
                    .collect(Collectors.joining(", "));
            Notifications.show(
                    "Usunięto " + ok + " faktur: " + numbers + ".",
                    NotificationVariant.SUCCESS);
            return;
        }
        String failureDetails = result.failures().stream()
                .map(failure -> invoiceLabel(failure) + ": " + failure.message())
                .collect(Collectors.joining("; "));
        if (ok == 0) {
            showError("Nie usunięto żadnej faktury. " + failureDetails);
            return;
        }
        Notifications.show(
                "Usunięto " + ok + " faktur, nie udało się usunąć " + failed + ": " + failureDetails,
                NotificationVariant.LUMO_PRIMARY);
    }

    private void updateDeleteSelectedButton() {
        deleteSelectedButton.setEnabled(!selectedDeletableInvoices().isEmpty());
    }

    private List<SalesInvoiceDto> selectedDeletableInvoices() {
        List<SalesInvoiceDto> selected = new ArrayList<>();
        for (SalesInvoiceDto invoice : grid.getSelectedItems()) {
            if (invoice != null && invoice.sihId() != null && !invoice.sihId().isBlank()) {
                selected.add(invoice);
            }
        }
        return selected;
    }

    private static String invoiceLabel(SalesInvoiceDto invoice) {
        if (invoice.invoiceNo() == null || invoice.invoiceNo().isBlank()) {
            return invoice.sihId();
        }
        return invoice.invoiceNo();
    }

    private static String invoiceLabel(BulkDeleteInvoiceFailure failure) {
        if (failure.invoiceNo() == null || failure.invoiceNo().isBlank()) {
            return failure.id();
        }
        return failure.invoiceNo();
    }

    private void deleteInvoice(SalesInvoiceDto invoice, Button deleteButton) {
        deleteButton.setEnabled(false);
        String invoiceNo = invoice.invoiceNo() == null || invoice.invoiceNo().isBlank()
                ? invoice.sihId()
                : invoice.invoiceNo();
        try {
            var result = invoicesService.deleteInvoice(invoice.sihId());
            String deletedInvoiceNo = result.invoiceNo() == null || result.invoiceNo().isBlank()
                    ? invoiceNo
                    : result.invoiceNo();
            if (result.paymentDocumentNos() != null && !result.paymentDocumentNos().isEmpty()) {
                Notifications.show(
                        "Usunięto płatności " + String.join(", ", result.paymentDocumentNos())
                                + " oraz fakturę " + deletedInvoiceNo + ".",
                        NotificationVariant.SUCCESS);
            } else {
                Notifications.show("Usunięto fakturę " + deletedInvoiceNo + ".", NotificationVariant.SUCCESS);
            }
            loadInvoices();
        } catch (ResponseStatusException ex) {
            showError(reason(ex));
        } catch (RestClientResponseException ex) {
            showError(MeritErrorMessages.from(ex));
        } catch (RuntimeException ex) {
            showError("Nie udało się usunąć faktury " + invoiceNo + ".");
        } finally {
            deleteButton.setEnabled(true);
        }
    }

    private void sendInvoiceByEmail(SalesInvoiceDto invoice, Button emailButton) {
        emailButton.setEnabled(false);
        String invoiceNo = invoice.invoiceNo() == null || invoice.invoiceNo().isBlank()
                ? invoice.sihId()
                : invoice.invoiceNo();
        try {
            invoicesService.sendInvoiceByEmail(invoice.sihId(), false);
            Notifications.show("Wysłano fakturę " + invoiceNo + ".", NotificationVariant.SUCCESS);
            loadInvoices();
        } catch (ResponseStatusException ex) {
            showError(emailErrorMessage(ex, invoiceNo));
        } catch (RestClientResponseException ex) {
            showError(MeritErrorMessages.from(ex));
        } catch (RuntimeException ex) {
            showError("Nie udało się wysłać faktury " + invoiceNo + ".");
        } finally {
            emailButton.setEnabled(true);
        }
    }

    private void loadInvoices() {
        LocalDate from = fromPicker.getValue();
        LocalDate to = toPicker.getValue();
        if (from == null || to == null) {
            showError("Podaj zakres dat.");
            return;
        }
        try {
            List<SalesInvoiceDto> invoices = invoicesService.getInvoices(from, to);
            grid.setItems(invoices);
            grid.deselectAll();
            updateDeleteSelectedButton();
        } catch (ResponseStatusException ex) {
            showError(reason(ex));
        } catch (RestClientResponseException ex) {
            showError(MeritErrorMessages.from(ex));
        } catch (RuntimeException ex) {
            showError("Nie udało się pobrać faktur.");
        }
    }

    private String formatAmount(BigDecimal amount) {
        return amount == null ? "" : amountFormat.format(amount);
    }

    private String formatAmountWithCurrency(BigDecimal amount, String currencySuffix) {
        if (amount == null) {
            return null;
        }
        return amountFormat.format(amount) + currencySuffix;
    }

    private String formatEmailSentAt(Instant emailSentAt) {
        if (emailSentAt == null) {
            return "";
        }
        return EMAIL_SENT_FORMAT.format(emailSentAt.atZone(ZONE));
    }

    private static String reason(ResponseStatusException ex) {
        if (ex.getReason() == null || ex.getReason().isBlank()) {
            return "Nie udało się pobrać faktur.";
        }
        return ex.getReason();
    }

    private static String emailErrorMessage(ResponseStatusException ex, String invoiceNo) {
        if (ex.getReason() == null || ex.getReason().isBlank()) {
            return "Nie udało się wysłać faktury " + invoiceNo + ".";
        }
        return ex.getReason();
    }

    private static void showError(String message) {
        Notification notification = Notification.show(message, 5000, Notification.Position.TOP_END);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
}
