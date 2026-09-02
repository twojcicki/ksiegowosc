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
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;

import pl.tw.ksiegowosc.client.AllegroErrorMessages;
import pl.tw.ksiegowosc.dto.AllegroOfferDto;
import pl.tw.ksiegowosc.dto.AllegroSoldItemDto;
import pl.tw.ksiegowosc.service.AllegroAuthService;
import pl.tw.ksiegowosc.service.AllegroOffersService;
import pl.tw.ksiegowosc.service.AllegroOrdersService;

@Route("allegro")
@PageTitle("Allegro")
public class AllegroView extends VerticalLayout {

    private static final Locale PL = Locale.forLanguageTag("pl-PL");
    private static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", PL);

    private final AllegroAuthService authService;
    private final AllegroOffersService offersService;
    private final AllegroOrdersService ordersService;
    private final NumberFormat amountFormat;

    private final VerticalLayout connectBanner = new VerticalLayout();
    private final VerticalLayout contentLayout = new VerticalLayout();
    private final Grid<AllegroOfferDto> offersGrid = new Grid<>(AllegroOfferDto.class, false);
    private final Grid<AllegroSoldItemDto> soldGrid = new Grid<>(AllegroSoldItemDto.class, false);
    private final DatePicker soldFromPicker = new DatePicker("Od");
    private final DatePicker soldToPicker = new DatePicker("Do");

    public AllegroView(
            AllegroAuthService authService,
            AllegroOffersService offersService,
            AllegroOrdersService ordersService) {
        this.authService = authService;
        this.offersService = offersService;
        this.ordersService = ordersService;
        this.amountFormat = NumberFormat.getNumberInstance(PL);
        this.amountFormat.setMinimumFractionDigits(2);
        this.amountFormat.setMaximumFractionDigits(2);

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        RouterLink invoicesLink = new RouterLink("Faktury", InvoiceListView.class);
        HorizontalLayout nav = new HorizontalLayout(invoicesLink);
        nav.setWidthFull();

        HorizontalLayout titleRow = new HorizontalLayout(new H2("Allegro Sandbox"));
        titleRow.setAlignItems(Alignment.CENTER);
        titleRow.setWidthFull();

        configureConnectBanner();
        configureOffersGrid();
        configureSoldGrid();

        Tab offersTab = new Tab("Oferty");
        Tab soldTab = new Tab("Sprzedane");
        Tabs tabs = new Tabs(offersTab, soldTab);
        tabs.setWidthFull();

        VerticalLayout offersPanel = new VerticalLayout(createOffersToolbar(), offersGrid);
        offersPanel.setPadding(false);
        offersPanel.setSpacing(true);
        offersPanel.setSizeFull();
        offersPanel.setFlexGrow(1, offersGrid);

        Button soldSearch = new Button("Szukaj", event -> loadSoldItems());
        soldSearch.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        soldFromPicker.setLocale(PL);
        soldToPicker.setLocale(PL);
        LocalDate today = LocalDate.now();
        soldFromPicker.setValue(today.minusDays(30));
        soldToPicker.setValue(today);
        HorizontalLayout soldFilters = new HorizontalLayout(soldFromPicker, soldToPicker, soldSearch);
        soldFilters.setAlignItems(Alignment.END);
        soldFilters.setWidthFull();

        VerticalLayout soldPanel = new VerticalLayout(soldFilters, soldGrid);
        soldPanel.setPadding(false);
        soldPanel.setSpacing(true);
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

        contentLayout.add(tabs, offersPanel, soldPanel);
        contentLayout.setSizeFull();
        contentLayout.setFlexGrow(1, offersPanel);
        contentLayout.setFlexGrow(1, soldPanel);
        contentLayout.setPadding(false);
        contentLayout.setSpacing(true);

        add(nav, titleRow, connectBanner, contentLayout);
        setFlexGrow(1, contentLayout);

        refreshConnectionState();
    }

    private void configureConnectBanner() {
        connectBanner.setPadding(false);
        connectBanner.setSpacing(false);
        connectBanner.setVisible(false);
    }

    private HorizontalLayout createOffersToolbar() {
        Button refresh = new Button("Odśwież", event -> loadOffers());
        refresh.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        HorizontalLayout toolbar = new HorizontalLayout(refresh);
        toolbar.setWidthFull();
        return toolbar;
    }

    private void configureOffersGrid() {
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
        soldGrid.addColumn(AllegroSoldItemDto::orderId).setHeader("ID zamówienia").setAutoWidth(true).setSortable(true);
        soldGrid.addColumn(AllegroSoldItemDto::name).setHeader("Nazwa").setFlexGrow(1).setSortable(true);
        soldGrid.addColumn(AllegroSoldItemDto::quantity).setHeader("Ilość").setAutoWidth(true).setSortable(true);
        soldGrid.addColumn(item -> formatAmount(item.price(), item.currency()))
                .setHeader("Cena")
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
        soldGrid.setSizeFull();
    }

    private void refreshConnectionState() {
        boolean connected = authService.isConnected();
        connectBanner.removeAll();
        connectBanner.setVisible(!connected);
        contentLayout.setVisible(connected);

        if (!connected) {
            Anchor connectLink = new Anchor("/api/allegro/auth/connect", "Połącz z Allegro");
            connectLink.getElement().setAttribute("router-ignore", true);
            connectBanner.add(
                    new Paragraph("Konto Allegro nie jest połączone. Kliknij poniżej, aby autoryzować aplikację w Sandbox."),
                    connectLink);
        } else {
            loadOffers();
        }
    }

    private void loadOffers() {
        if (!authService.isConnected()) {
            return;
        }
        try {
            List<AllegroOfferDto> offers = offersService.getOffers(0, 100, null);
            offersGrid.setItems(offers);
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
            List<AllegroSoldItemDto> items = ordersService.getSoldItems(from, to, 0, 100);
            soldGrid.setItems(items);
        } catch (ResponseStatusException ex) {
            showError(reason(ex));
        } catch (RestClientResponseException ex) {
            showError(AllegroErrorMessages.from(ex));
        } catch (RuntimeException ex) {
            showError("Nie udało się pobrać sprzedanych pozycji.");
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
}
