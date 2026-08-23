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

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import pl.tw.ksiegowosc.client.MeritErrorMessages;
import pl.tw.ksiegowosc.dto.SalesInvoiceDto;
import pl.tw.ksiegowosc.service.InvoicesService;

@Route("")
@PageTitle("Faktury")
public class InvoiceListView extends VerticalLayout {

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

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        fromPicker.setLocale(PL);
        toPicker.setLocale(PL);
        LocalDate today = LocalDate.now();
        fromPicker.setValue(today.minusDays(30));
        toPicker.setValue(today);

        Button search = new Button("Szukaj", event -> loadInvoices());
        search.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button addInvoice = new Button("Dodaj fakturę", event -> openCreateInvoiceDialog());
        addInvoice.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout titleRow = new HorizontalLayout(addInvoice, new H2("Faktury"));
        titleRow.setAlignItems(Alignment.CENTER);
        titleRow.setWidthFull();

        HorizontalLayout filters = new HorizontalLayout(fromPicker, toPicker, search);
        filters.setAlignItems(Alignment.END);
        filters.setWidthFull();

        configureGrid();

        add(titleRow, filters, grid);
        setFlexGrow(1, grid);

        loadInvoices();
    }

    private void openCreateInvoiceDialog() {
        CreateInvoiceDialog dialog = new CreateInvoiceDialog(invoicesService, this::loadInvoices);
        dialog.open();
    }

    private void configureGrid() {
        grid.addColumn(SalesInvoiceDto::invoiceNo)
                .setHeader("Numer")
                .setAutoWidth(true)
                .setSortable(true);
        grid.addColumn(SalesInvoiceDto::documentDate)
                .setHeader("Data")
                .setAutoWidth(true)
                .setSortable(true);
        grid.addColumn(SalesInvoiceDto::customerName)
                .setHeader("Klient")
                .setFlexGrow(1)
                .setSortable(true);
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
        grid.addComponentColumn(invoice -> createEmailButton(invoice))
                .setHeader("Akcje")
                .setAutoWidth(true);
        grid.setSizeFull();
    }

    private Button createEmailButton(SalesInvoiceDto invoice) {
        Button emailButton = new Button("E-mail");
        emailButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        emailButton.setEnabled(invoice.sihId() != null && !invoice.sihId().isBlank());
        emailButton.addClickListener(event -> confirmSendEmail(invoice, emailButton));
        return emailButton;
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
            showSuccess("Wysłano fakturę " + invoiceNo + ".");
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

    private static void showSuccess(String message) {
        Notification notification = Notification.show(message, 5000, Notification.Position.TOP_END);
        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    private static void showError(String message) {
        Notification notification = Notification.show(message, 5000, Notification.Position.TOP_END);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
}
