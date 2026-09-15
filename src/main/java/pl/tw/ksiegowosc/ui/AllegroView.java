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
import com.vaadin.flow.component.combobox.ComboBox;
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
import pl.tw.ksiegowosc.dto.AllegroInvoicePreviewDto;
import pl.tw.ksiegowosc.dto.AllegroInvoicePreviewRow;
import pl.tw.ksiegowosc.dto.AllegroOfferDto;
import pl.tw.ksiegowosc.dto.AllegroSoldItemDto;
import pl.tw.ksiegowosc.dto.IssueAllegroInvoiceResponse;
import pl.tw.ksiegowosc.mapper.AllegroMeritInvoiceMappings;
import pl.tw.ksiegowosc.mapper.MeritFieldRule;
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
    private final Grid<MeritFieldRule> rulesGrid = new Grid<>(MeritFieldRule.class, false);
    private final Grid<AllegroInvoicePreviewRow> previewGrid = new Grid<>(AllegroInvoicePreviewRow.class, false);
    private final DatePicker soldFromPicker = new DatePicker("Od");
    private final DatePicker soldToPicker = new DatePicker("Do");
    private final ComboBox<AllegroSoldItemDto> previewOrderCombo = new ComboBox<>("Sprzedaż do podglądu");

    private final Tab offersTab = new Tab("Oferty");
    private final Tab soldTab = new Tab("Sprzedane");
    private final Tab mappingTab = new Tab("Mapowanie");
    private final Tabs tabs = new Tabs(offersTab, soldTab, mappingTab);
    private final Tab mappingRulesTab = new Tab("Reguły");
    private final Tab mappingPreviewTab = new Tab("Podgląd");
    private final Tabs mappingTabs = new Tabs(mappingRulesTab, mappingPreviewTab);

    private VerticalLayout offersPanel;
    private VerticalLayout soldPanel;
    private VerticalLayout mappingPanel;
    private VerticalLayout mappingRulesPanel;
    private VerticalLayout mappingPreviewPanel;
    private List<AllegroSoldItemDto> lastSoldItems = List.of();

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
        configureRulesGrid();
        configurePreviewGrid();
        add(createHeader(), connectBanner, createContent());
        refreshConnectionState();
    }

    private ViewHeader createHeader() {
        DrawerToggle toggle = new DrawerToggle();
        toggle.addThemeVariants(ButtonVariant.TERTIARY);
        return new ViewHeader(toggle, new H1("Allegro"));
    }

    private VerticalLayout createContent() {
        tabs.setWidthFull();

        Button refresh = new Button("Odśwież", e -> loadOffers());
        refresh.addThemeVariants(ButtonVariant.PRIMARY);
        HorizontalLayout offersToolbar = new HorizontalLayout(refresh);
        offersToolbar.addClassName("filters");
        offersToolbar.setWidthFull();

        offersPanel = new VerticalLayout(offersToolbar, offersGrid);
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

        soldPanel = new VerticalLayout(soldFilters, soldGrid);
        soldPanel.setPadding(false);
        soldPanel.setSpacing(false);
        soldPanel.setSizeFull();
        soldPanel.setFlexGrow(1, soldGrid);
        soldPanel.setVisible(false);

        mappingPanel = createMappingPanel();
        mappingPanel.setVisible(false);

        tabs.addSelectedChangeListener(event -> showSelectedTab(event.getSelectedTab()));

        contentLayout.setPadding(false);
        contentLayout.setSpacing(false);
        contentLayout.setSizeFull();
        contentLayout.add(tabs, offersPanel, soldPanel, mappingPanel);
        contentLayout.setFlexGrow(1, offersPanel);
        contentLayout.setFlexGrow(1, soldPanel);
        contentLayout.setFlexGrow(1, mappingPanel);
        return contentLayout;
    }

    private VerticalLayout createMappingPanel() {
        rulesGrid.setItems(AllegroMeritInvoiceMappings.RULES);
        rulesGrid.setSizeFull();

        mappingRulesPanel = new VerticalLayout(rulesGrid);
        mappingRulesPanel.setPadding(false);
        mappingRulesPanel.setSpacing(false);
        mappingRulesPanel.setSizeFull();
        mappingRulesPanel.setFlexGrow(1, rulesGrid);

        previewOrderCombo.setItemLabelGenerator(item -> {
            if (item == null) {
                return "";
            }
            String account = item.accountName() == null ? "" : item.accountName() + " · ";
            String name = item.name() == null ? "" : " — " + item.name();
            return account + item.orderId() + name;
        });
        previewOrderCombo.setWidthFull();
        previewOrderCombo.setClearButtonVisible(true);

        Button previewButton = new Button("Podgląd", e -> loadPreview());
        previewButton.addThemeVariants(ButtonVariant.PRIMARY);
        HorizontalLayout previewToolbar = new HorizontalLayout(previewOrderCombo, previewButton);
        previewToolbar.setWidthFull();
        previewToolbar.setFlexGrow(1, previewOrderCombo);
        previewToolbar.addClassName("filters");

        Paragraph hint = new Paragraph(
                "Podgląd buduje ten sam payload co wystawienie faktury, bez sendinvoice i bez tworzenia klienta.");
        hint.getStyle().set("margin-top", "0");

        mappingPreviewPanel = new VerticalLayout(hint, previewToolbar, previewGrid);
        mappingPreviewPanel.setPadding(false);
        mappingPreviewPanel.setSpacing(true);
        mappingPreviewPanel.setSizeFull();
        mappingPreviewPanel.setFlexGrow(1, previewGrid);
        mappingPreviewPanel.setVisible(false);

        mappingTabs.setWidthFull();
        mappingTabs.addSelectedChangeListener(event -> showMappingSubTab(event.getSelectedTab()));

        VerticalLayout panel = new VerticalLayout(mappingTabs, mappingRulesPanel, mappingPreviewPanel);
        panel.setPadding(false);
        panel.setSpacing(false);
        panel.setSizeFull();
        panel.setFlexGrow(1, mappingRulesPanel);
        panel.setFlexGrow(1, mappingPreviewPanel);
        return panel;
    }

    private void showMappingSubTab(Tab selected) {
        boolean rulesSelected = selected == mappingRulesTab;
        mappingRulesPanel.setVisible(rulesSelected);
        mappingPreviewPanel.setVisible(!rulesSelected);
        if (!rulesSelected) {
            refreshPreviewOrderChoices();
        }
    }

    private void showSelectedTab(Tab selected) {
        offersPanel.setVisible(selected == offersTab);
        soldPanel.setVisible(selected == soldTab);
        mappingPanel.setVisible(selected == mappingTab);
        if (selected == offersTab) {
            loadOffers();
        } else if (selected == soldTab) {
            loadSoldItems();
        } else if (selected == mappingTab) {
            showMappingSubTab(mappingTabs.getSelectedTab());
        }
    }

    private void configureOffersGrid() {
        offersGrid.addThemeVariants(GridVariant.NO_BORDER);
        offersGrid.addColumn(AllegroOfferDto::accountName).setHeader("Konto").setAutoWidth(true).setSortable(true);
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
        soldGrid.addColumn(AllegroSoldItemDto::accountName).setHeader("Konto").setAutoWidth(true).setSortable(true);
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
        soldGrid.addComponentColumn(this::createSoldActions)
                .setHeader("Akcja")
                .setAutoWidth(true)
                .setFlexGrow(0);
        soldGrid.setSizeFull();
    }

    private void configureRulesGrid() {
        rulesGrid.addThemeVariants(GridVariant.NO_BORDER);
        rulesGrid.addColumn(rule -> rule.section().name()).setHeader("Sekcja").setAutoWidth(true).setSortable(true);
        rulesGrid.addColumn(MeritFieldRule::meritField).setHeader("Pole Merit").setAutoWidth(true).setSortable(true);
        rulesGrid.addColumn(MeritFieldRule::sourceRule).setHeader("Źródło / reguła").setFlexGrow(1);
        rulesGrid.setSizeFull();
    }

    private void configurePreviewGrid() {
        previewGrid.addThemeVariants(GridVariant.NO_BORDER);
        previewGrid.addColumn(row -> row.section().name()).setHeader("Sekcja").setAutoWidth(true).setSortable(true);
        previewGrid.addColumn(AllegroInvoicePreviewRow::meritField).setHeader("Pole Merit").setAutoWidth(true);
        previewGrid.addColumn(AllegroInvoicePreviewRow::sourceRule).setHeader("Reguła").setFlexGrow(1);
        previewGrid.addColumn(AllegroInvoicePreviewRow::value).setHeader("Wartość").setFlexGrow(1);
        previewGrid.setSizeFull();
    }

    private HorizontalLayout createSoldActions(AllegroSoldItemDto item) {
        boolean alreadyIssued = item.invoiceNo() != null && !item.invoiceNo().isBlank();
        Button issue = new Button("Wystaw fakturę");
        issue.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY);
        issue.setEnabled(!alreadyIssued);
        if (alreadyIssued) {
            issue.getElement().setAttribute("title", "Faktura już wystawiona");
        }
        issue.addClickListener(event -> issueInvoice(item, issue));

        Button preview = new Button("Podgląd mapowania");
        preview.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        preview.addClickListener(event -> openMappingPreview(item));

        HorizontalLayout actions = new HorizontalLayout(issue, preview);
        actions.setSpacing(true);
        actions.setPadding(false);
        return actions;
    }

    private void openMappingPreview(AllegroSoldItemDto item) {
        mappingTabs.setSelectedTab(mappingPreviewTab);
        tabs.setSelectedTab(mappingTab);
        previewOrderCombo.setValue(item);
        loadPreview();
    }

    private void issueInvoice(AllegroSoldItemDto item, Button button) {
        button.setEnabled(false);
        try {
            IssueAllegroInvoiceResponse response = invoiceService.issueInvoice(item.accountId(), item.orderId());
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

    private void loadPreview() {
        AllegroSoldItemDto selected = previewOrderCombo.getValue();
        if (selected == null) {
            showError("Wybierz sprzedaż do podglądu.");
            return;
        }
        try {
            AllegroInvoicePreviewDto preview = invoiceService.previewInvoice(selected.accountId(), selected.orderId());
            previewGrid.setItems(preview.rows());
            String customerNote = preview.customerExists()
                    ? "Klient istnieje w Merit: " + preview.customerId()
                    : "Klient zostanie utworzony przy wystawieniu.";
            showSuccess("Podgląd mapowania dla " + preview.orderId() + ". " + customerNote);
        } catch (ResponseStatusException ex) {
            showError(reason(ex));
        } catch (RestClientResponseException ex) {
            String message = MeritErrorMessages.from(ex);
            if (message == null || message.isBlank()) {
                message = AllegroErrorMessages.from(ex);
            }
            showError(message);
        } catch (RuntimeException ex) {
            showError("Nie udało się zbudować podglądu mapowania.");
        }
    }

    private void refreshConnectionState() {
        boolean connected = authService.isConnected();
        connectBanner.removeAll();
        connectBanner.setVisible(!connected);
        contentLayout.setVisible(connected);
        connectBanner.addClassName("banner");

        if (!connected) {
            Button settingsButton = new Button("Ustawienia API", event -> UI.getCurrent().navigate("ustawienia-api"));
            settingsButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            connectBanner.add(
                    new Paragraph(
                            "Brak połączonego konta Allegro. Dodaj i połącz konto w Ustawieniach API."),
                    settingsButton);
        } else {
            loadOffers();
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
            lastSoldItems = ordersService.getSoldItems(from, to, 0, 100);
            soldGrid.setItems(lastSoldItems);
            refreshPreviewOrderChoices();
        } catch (ResponseStatusException ex) {
            showError(reason(ex));
        } catch (RestClientResponseException ex) {
            showError(AllegroErrorMessages.from(ex));
        } catch (RuntimeException ex) {
            showError("Nie udało się pobrać sprzedanych zamówień.");
        }
    }

    private void refreshPreviewOrderChoices() {
        AllegroSoldItemDto current = previewOrderCombo.getValue();
        previewOrderCombo.setItems(lastSoldItems);
        if (current != null) {
            lastSoldItems.stream()
                    .filter(item -> item.orderId().equals(current.orderId())
                            && java.util.Objects.equals(item.accountId(), current.accountId()))
                    .findFirst()
                    .ifPresentOrElse(previewOrderCombo::setValue, () -> previewOrderCombo.clear());
        }
        if (lastSoldItems.isEmpty() && authService.isConnected()) {
            LocalDate from = soldFromPicker.getValue();
            LocalDate to = soldToPicker.getValue();
            if (from != null && to != null) {
                try {
                    lastSoldItems = ordersService.getSoldItems(from, to, 0, 100);
                    previewOrderCombo.setItems(lastSoldItems);
                } catch (RuntimeException ignored) {
                    // lista pozostaje pusta; użytkownik zobaczy błąd przy Podgląd
                }
            }
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
