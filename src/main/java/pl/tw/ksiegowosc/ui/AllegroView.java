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

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.PermitAll;
import pl.tw.ksiegowosc.client.AllegroErrorMessages;
import pl.tw.ksiegowosc.client.MeritErrorMessages;
import pl.tw.ksiegowosc.dto.AllegroOfferDto;
import pl.tw.ksiegowosc.dto.AllegroSoldItemDto;
import pl.tw.ksiegowosc.dto.IssueAllegroInvoiceResponse;
import pl.tw.ksiegowosc.service.AllegroAuthService;
import pl.tw.ksiegowosc.service.AllegroInvoiceService;
import pl.tw.ksiegowosc.service.AllegroOffersService;
import pl.tw.ksiegowosc.service.AllegroOrdersService;
import pl.tw.ksiegowosc.ui.component.View;
import pl.tw.ksiegowosc.ui.component.ViewHeader;
import pl.tw.ksiegowosc.ui.util.Aura;

@Route("allegro")
@PageTitle("Allegro")
@PermitAll
public class AllegroView extends View {

    private static final Locale PL = Locale.forLanguageTag("pl-PL");
    private static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", PL);

    private final AllegroAuthService authService;
    private final AllegroOffersService offersService;
    private final AllegroOrdersService ordersService;
    private final AllegroInvoiceService invoiceService;
    private final NumberFormat amountFormat;

    private final Div connectBanner = new Div();
    private final VerticalLayout contentLayout = new VerticalLayout();
    private final Grid<AllegroOfferDto> offersGrid = new Grid<>(AllegroOfferDto.class, false);
    private final Grid<AllegroSoldItemDto> soldGrid = new Grid<>(AllegroSoldItemDto.class, false);
    private final DatePicker soldFromPicker = new DatePicker("Od");
    private final DatePicker soldToPicker = new DatePicker("Do");

    public AllegroView(
            AllegroAuthService authService,
            AllegroOffersService offersService,
            AllegroOrdersService ordersService,
            AllegroInvoiceService invoiceService) {
        this.authService = authService;
        this.offersService = offersService;
        this.ordersService = ordersService;
        this.invoiceService = invoiceService;
        this.amountFormat = NumberFormat.getNumberInstance(PL);
        this.amountFormat.setMinimumFractionDigits(2);
        this.amountFormat.setMaximumFractionDigits(2);

        addClassNames(Aura.SURFACE_SOLID, "allegro-view");
        configureOffersGrid();
        configureSoldGrid();
        add(createHeader(), connectBanner, createContent());
        refreshConnectionState();
    }

    private ViewHeader createHeader() {
        DrawerToggle toggle = new DrawerToggle();
        toggle.addThemeVariants(ButtonVariant.TERTIARY);
        return new ViewHeader(toggle, new H1("Allegro"));
    }

    private VerticalLayout createContent() {
        Tab offersTab = new Tab("Oferty");
        Tab soldTab = new Tab("Sprzedane");
        Tabs tabs = new Tabs(offersTab, soldTab);
        tabs.setWidthFull();

        Button refresh = new Button("Odśwież", e -> loadOffers());
        refresh.addThemeVariants(ButtonVariant.PRIMARY);
        HorizontalLayout offersToolbar = new HorizontalLayout(refresh);
        offersToolbar.addClassName("filters");
        offersToolbar.setWidthFull();

        VerticalLayout offersPanel = new VerticalLayout(offersToolbar, offersGrid);
        offersPanel.setPadding(false);
        offersPanel.setSpacing(false);
        offersPanel.setSizeFull();
        offersPanel.setFlexGrow(1, offersGrid);

        Button soldSearch = new Button("Szukaj", e -> loadSoldItems());
        soldSearch.addThemeVariants(ButtonVariant.PRIMARY);
        soldFromPicker.setLocale(PL);
        soldToPicker.setLocale(PL);
        LocalDate today = LocalDate.now();
        soldFromPicker.setValue(today.minusDays(30));
        soldToPicker.setValue(today);
        HorizontalLayout soldFilters = new HorizontalLayout(soldFromPicker, soldToPicker, soldSearch);
        soldFilters.addClassName("filters");
        soldFilters.setWidthFull();

        VerticalLayout soldPanel = new VerticalLayout(soldFilters, soldGrid);
        soldPanel.setPadding(false);
        soldPanel.setSpacing(false);
        soldPanel.setSizeFull();
        soldPanel.setFlexGrow(1, soldGrid);
        soldPanel.setVisible(false);

        tabs.addSelectedChangeListener(event -> {
            boolean offersSelected = event.getSelectedTab() == offersTab;
            offersPanel.setVisible(offersSelected);
            soldPanel.setVisible(!offersSelected);
            if (offersSelected) {
                loadOffers();
            } else {
                loadSoldItems();
            }
        });

        contentLayout.setPadding(false);
        contentLayout.setSpacing(false);
        contentLayout.setSizeFull();
        contentLayout.add(tabs, offersPanel, soldPanel);
        contentLayout.setFlexGrow(1, offersPanel);
        contentLayout.setFlexGrow(1, soldPanel);
        return contentLayout;
    }

    private void configureOffersGrid() {
        offersGrid.addThemeVariants(GridVariant.NO_BORDER);
        offersGrid.addColumn(AllegroOfferDto::id).setHeader("ID oferty").setAutoWidth(true).setSortable(true);
        offersGrid.addColumn(AllegroOfferDto::name).setHeader("Nazwa").setFlexGrow(1).setSortable(true);
        offersGrid.addColumn(offer -> formatAmount(offer.price(), offer.currency()))
                .setHeader("Cena")
                .setAutoWidth(true);
        offersGrid.addColumn(AllegroOfferDto::available).setHeader("Dostępne").setAutoWidth(true).setSortable(true);
        offersGrid.addColumn(AllegroOfferDto::sold).setHeader("Sprzedane").setAutoWidth(true).setSortable(true);
        offersGrid.addColumn(AllegroOfferDto::publicationStatus)
                .setHeader("Status")
                .setAutoWidth(true)
                .setSortable(true);
        offersGrid.setSizeFull();
    }

    private void configureSoldGrid() {
        soldGrid.addThemeVariants(GridVariant.NO_BORDER);
        soldGrid.addColumn(AllegroSoldItemDto::orderId).setHeader("ID zamówienia").setAutoWidth(true).setSortable(true);
        soldGrid.addColumn(AllegroSoldItemDto::name).setHeader("Pozycje").setFlexGrow(1).setSortable(true);
        soldGrid.addColumn(AllegroSoldItemDto::itemCount).setHeader("Liczba pozycji").setAutoWidth(true).setSortable(true);
        soldGrid.addColumn(item -> formatAmount(item.totalGross(), item.currency()))
                .setHeader("Suma brutto")
                .setAutoWidth(true);
        soldGrid.addColumn(item -> formatInstant(item.boughtAt()))
                .setHeader("Data zakupu")
                .setAutoWidth(true)
                .setSortable(true);
        soldGrid.addColumn(AllegroSoldItemDto::buyerLogin).setHeader("Kupujący").setAutoWidth(true).setSortable(true);
        soldGrid.addColumn(AllegroSoldItemDto::fulfillmentStatus)
                .setHeader("Realizacja")
                .setAutoWidth(true)
                .setSortable(true);
        soldGrid.addColumn(item -> {
            String invoiceNo = item.invoiceNo();
            return invoiceNo == null || invoiceNo.isBlank() ? "—" : invoiceNo;
        }).setHeader("Faktura").setAutoWidth(true).setSortable(true);
        soldGrid.addComponentColumn(this::createIssueInvoiceButton)
                .setHeader("Akcja")
                .setAutoWidth(true)
                .setFlexGrow(0);
        soldGrid.setSizeFull();
    }

    private Button createIssueInvoiceButton(AllegroSoldItemDto item) {
        boolean alreadyIssued = item.invoiceNo() != null && !item.invoiceNo().isBlank();
        Button button = new Button("Wystaw fakturę");
        button.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY);
        button.setEnabled(!alreadyIssued);
        if (alreadyIssued) {
            button.getElement().setAttribute("title", "Faktura już wystawiona");
        }
        button.addClickListener(event -> issueInvoice(item, button));
        return button;
    }

    private void issueInvoice(AllegroSoldItemDto item, Button button) {
        button.setEnabled(false);
        try {
            IssueAllegroInvoiceResponse response = invoiceService.issueInvoice(item.orderId());
            showSuccess("Wystawiono fakturę " + response.invoiceNo() + ".");
            loadSoldItems();
        } catch (ResponseStatusException ex) {
            showError(reason(ex));
            if (item.invoiceNo() == null || item.invoiceNo().isBlank()) {
                button.setEnabled(true);
            }
        } catch (RestClientResponseException ex) {
            String message = MeritErrorMessages.from(ex);
            if (message == null || message.isBlank()) {
                message = AllegroErrorMessages.from(ex);
            }
            showError(message);
            button.setEnabled(true);
        } catch (RuntimeException ex) {
            showError("Nie udało się wystawić faktury.");
            button.setEnabled(true);
        }
    }

    private void refreshConnectionState() {
        boolean connected = authService.isConnected();
        connectBanner.removeAll();
        connectBanner.setVisible(!connected);
        contentLayout.setVisible(connected);
        connectBanner.addClassName("banner");

        if (!connected) {
            Button connectButton = new Button("Połącz z Allegro", event -> connectAllegro());
            connectButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            connectBanner.add(
                    new Paragraph("Konto Allegro nie jest połączone. Kliknij poniżej, aby autoryzować aplikację w Sandbox."),
                    connectButton);
        } else {
            loadOffers();
        }
    }

    private void connectAllegro() {
        try {
            UI.getCurrent().getPage().setLocation(authService.buildAuthorizationUrl());
        } catch (ResponseStatusException ex) {
            showError(reason(ex));
        } catch (RuntimeException ex) {
            showError("Nie udało się rozpocząć połączenia z Allegro.");
        }
    }

    private void loadOffers() {
        if (!authService.isConnected()) {
            return;
        }
        try {
            offersGrid.setItems(offersService.getOffers(0, 100, null));
        } catch (ResponseStatusException ex) {
            showError(reason(ex));
        } catch (RestClientResponseException ex) {
            showError(AllegroErrorMessages.from(ex));
        } catch (RuntimeException ex) {
            showError("Nie udało się pobrać ofert Allegro.");
        }
    }

    private void loadSoldItems() {
        if (!authService.isConnected()) {
            return;
        }
        LocalDate from = soldFromPicker.getValue();
        LocalDate to = soldToPicker.getValue();
        if (from == null || to == null) {
            showError("Podaj zakres dat.");
            return;
        }
        try {
            soldGrid.setItems(ordersService.getSoldItems(from, to, 0, 100));
        } catch (ResponseStatusException ex) {
            showError(reason(ex));
        } catch (RestClientResponseException ex) {
            showError(AllegroErrorMessages.from(ex));
        } catch (RuntimeException ex) {
            showError("Nie udało się pobrać sprzedanych zamówień.");
        }
    }

    private String formatAmount(java.math.BigDecimal amount, String currency) {
        if (amount == null) {
            return "";
        }
        String formatted = amountFormat.format(amount);
        return currency == null || currency.isBlank() ? formatted : formatted + " " + currency;
    }

    private String formatInstant(Instant instant) {
        if (instant == null) {
            return "";
        }
        return DATE_TIME_FORMAT.format(instant.atZone(ZONE));
    }

    private static String reason(ResponseStatusException ex) {
        if (ex.getReason() == null || ex.getReason().isBlank()) {
            return "Operacja Allegro nie powiodła się.";
        }
        return ex.getReason();
    }

    private static void showError(String message) {
        Notification notification = Notification.show(message, 5000, Notification.Position.TOP_END);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }

    private static void showSuccess(String message) {
        Notification notification = Notification.show(message, 4000, Notification.Position.TOP_END);
        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }
}
