package pl.tw.ksiegowosc.ui;

import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

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
import pl.tw.ksiegowosc.dto.SalesInvoiceDetailsDto;
import pl.tw.ksiegowosc.dto.SalesInvoiceDto;
import pl.tw.ksiegowosc.dto.SalesInvoiceHeaderDto;
import pl.tw.ksiegowosc.dto.SalesInvoiceLineDto;
import pl.tw.ksiegowosc.service.InvoicesService;
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
    private final DatePicker fromPicker = new DatePicker("Od");
    private final DatePicker toPicker = new DatePicker("Do");
    private final Grid<SalesInvoiceDto> grid = new Grid<>(SalesInvoiceDto.class, false);
    private final NumberFormat amountFormat;

    public InvoiceListView(InvoicesService invoicesService) {
        this.invoicesService = invoicesService;
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

        HorizontalLayout filters = new HorizontalLayout(fromPicker, toPicker, search);
        filters.addClassName("filters");
        filters.setWidthFull();
        return filters;
    }

    private Grid<SalesInvoiceDto> createGrid() {
        grid.addThemeVariants(GridVariant.NO_BORDER);
        grid.addColumn(SalesInvoiceDto::invoiceNo).setHeader("Numer").setAutoWidth(true).setSortable(true);
        grid.addColumn(SalesInvoiceDto::documentDate).setHeader("Data").setAutoWidth(true).setSortable(true);
        grid.addColumn(SalesInvoiceDto::customerName).setHeader("Klient").setFlexGrow(1).setSortable(true);
        grid.addColumn(invoice -> invoice.totalAmount() == null ? "" : amountFormat.format(invoice.totalAmount()))
                .setHeader("Kwota")
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

            return new HorizontalLayout(details, email);
        }).setHeader("Akcje").setAutoWidth(true).setFlexGrow(0).setFrozenToEnd(true);
        grid.setSizeFull();
        return grid;
    }

    private void openCreateInvoiceDialog() {
        CreateInvoiceDialog dialog = new CreateInvoiceDialog(invoicesService, this::loadInvoices);
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
                content.add(detailRow("Klient", header.customerName()));
                content.add(detailRow("Data dokumentu", header.documentDate()));
                content.add(detailRow("Termin płatności", header.dueDate()));
                content.add(detailRow(
                        "Kwota",
                        header.totalAmount() == null
                                ? null
                                : amountFormat.format(header.totalAmount())
                                        + (header.currencyCode() == null ? "" : " " + header.currencyCode())));
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
        } catch (ResponseStatusException ex) {
            showError(reason(ex));
        } catch (RestClientResponseException ex) {
            showError(MeritErrorMessages.from(ex));
        } catch (RuntimeException ex) {
            showError("Nie udało się pobrać faktur.");
        }
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
