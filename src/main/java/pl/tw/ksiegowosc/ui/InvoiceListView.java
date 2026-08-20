package pl.tw.ksiegowosc.ui;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
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

        HorizontalLayout filters = new HorizontalLayout(fromPicker, toPicker, search);
        filters.setAlignItems(Alignment.END);
        filters.setWidthFull();

        configureGrid();

        add(new H2("Faktury"), filters, grid);
        setFlexGrow(1, grid);

        loadInvoices();
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
        grid.setSizeFull();
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

    private static String reason(ResponseStatusException ex) {
        if (ex.getReason() == null || ex.getReason().isBlank()) {
            return "Nie udało się pobrać faktur.";
        }
        return ex.getReason();
    }

    private static void showError(String message) {
        Notification notification = Notification.show(message, 5000, Notification.Position.MIDDLE);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
}
