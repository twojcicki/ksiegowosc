package pl.tw.ksiegowosc.ui;

import java.util.List;

import org.springframework.web.server.ResponseStatusException;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.PermitAll;
import pl.tw.ksiegowosc.dto.AllegroAccountDto;
import pl.tw.ksiegowosc.dto.UserApiSettingsDto;
import pl.tw.ksiegowosc.service.AllegroAccountService;
import pl.tw.ksiegowosc.service.AllegroAuthService;
import pl.tw.ksiegowosc.service.CurrentUserApiCredentialsService;
import pl.tw.ksiegowosc.ui.component.View;
import pl.tw.ksiegowosc.ui.component.ViewHeader;
import pl.tw.ksiegowosc.ui.util.Aura;
import pl.tw.ksiegowosc.ui.util.Notifications;

@Route("ustawienia-api")
@PageTitle("Ustawienia API")
@PermitAll
public class ApiSettingsView extends View {

    private final CurrentUserApiCredentialsService credentialsService;
    private final AllegroAccountService allegroAccountService;
    private final AllegroAuthService allegroAuthService;

    private final TextField meritApiId = new TextField("Merit Api Id");
    private final PasswordField meritApiKey = new PasswordField("Merit Api Key");
    private final TextField allegroName = new TextField("Nazwa konta");
    private final TextField allegroClientId = new TextField("Allegro Client ID");
    private final PasswordField allegroClientSecret = new PasswordField("Allegro Client Secret");
    private final Grid<AllegroAccountDto> allegroAccountsGrid = new Grid<>(AllegroAccountDto.class, false);

    public ApiSettingsView(
            CurrentUserApiCredentialsService credentialsService,
            AllegroAccountService allegroAccountService,
            AllegroAuthService allegroAuthService) {
        this.credentialsService = credentialsService;
        this.allegroAccountService = allegroAccountService;
        this.allegroAuthService = allegroAuthService;

        addClassNames(Aura.SURFACE_SOLID, "api-settings-view");
        configureAllegroGrid();
        add(createHeader(), createContent());
        loadSettings();
        loadAllegroAccounts();
    }

    private ViewHeader createHeader() {
        DrawerToggle toggle = new DrawerToggle();
        toggle.addThemeVariants(ButtonVariant.TERTIARY);
        return new ViewHeader(toggle, new H1("Ustawienia API"));
    }

    private VerticalLayout createContent() {
        meritApiId.setWidthFull();
        meritApiKey.setWidthFull();
        meritApiKey.setHelperText("Pozostaw puste, aby nie zmieniać zapisanego klucza.");

        Button saveMerit = new Button("Zapisz Merit", event -> saveMerit());
        saveMerit.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        FormLayout meritForm = new FormLayout(meritApiId, meritApiKey);
        meritForm.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
        VerticalLayout meritSection = new VerticalLayout(
                new H3("Merit Aktiva"),
                new Paragraph("Klucze API z Ustawienia → Ustawienia API w Merit."),
                meritForm,
                saveMerit);
        meritSection.setPadding(false);
        meritSection.setSpacing(true);
        meritSection.setWidthFull();

        allegroName.setWidthFull();
        allegroClientId.setWidthFull();
        allegroClientSecret.setWidthFull();

        Button addAllegro = new Button("Dodaj konto", event -> addAllegroAccount());
        addAllegro.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Span redirectUriHint = new Span();
        redirectUriHint.getStyle().set("font-size", "var(--lumo-font-size-s)");
        redirectUriHint.getStyle().set("word-break", "break-all");
        try {
            redirectUriHint.setText("Redirect URI (wpisz dokładnie to samo w Allegro Sandbox Developer Apps): "
                    + allegroAuthService.resolveRedirectUri());
        } catch (RuntimeException ex) {
            redirectUriHint.setText("Redirect URI: ustaw ALLEGRO_REDIRECT_URI na Render.");
        }

        FormLayout allegroForm = new FormLayout(allegroName, allegroClientId, allegroClientSecret);
        allegroForm.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
        VerticalLayout allegroSection = new VerticalLayout(
                new H3("Allegro"),
                new Paragraph("Dodaj aplikacje Allegro (Client ID/Secret). Połącz każde konto osobno."),
                allegroForm,
                addAllegro,
                redirectUriHint,
                allegroAccountsGrid);
        allegroSection.setPadding(false);
        allegroSection.setSpacing(true);
        allegroSection.setWidthFull();

        VerticalLayout content = new VerticalLayout(meritSection, allegroSection);
        content.setPadding(true);
        content.setSpacing(true);
        content.setMaxWidth("900px");
        return content;
    }

    private void configureAllegroGrid() {
        allegroAccountsGrid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.NO_BORDER);
        allegroAccountsGrid.setAllRowsVisible(true);
        allegroAccountsGrid.setWidthFull();
        allegroAccountsGrid.addColumn(AllegroAccountDto::name).setHeader("Nazwa").setFlexGrow(1);
        allegroAccountsGrid.addColumn(AllegroAccountDto::clientId).setHeader("Client ID").setFlexGrow(1);
        allegroAccountsGrid
                .addColumn(account -> account.clientSecretSet() ? "••••••••" : "—")
                .setHeader("Secret")
                .setAutoWidth(true)
                .setFlexGrow(0);
        allegroAccountsGrid
                .addColumn(account -> account.connected() ? "Połączone" : "Niepołączone")
                .setHeader("Status")
                .setAutoWidth(true)
                .setFlexGrow(0);
        allegroAccountsGrid
                .addComponentColumn(account -> {
                    Button connect = new Button("Połącz", e -> connectAllegro(account.id()));
                    connect.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY);
                    Button remove = new Button("Usuń", e -> deleteAllegroAccount(account.id()));
                    remove.addThemeVariants(
                            ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
                    HorizontalLayout actions = new HorizontalLayout(connect, remove);
                    actions.setAlignItems(FlexComponent.Alignment.CENTER);
                    return actions;
                })
                .setHeader("Akcje")
                .setAutoWidth(true)
                .setFlexGrow(0);
    }

    private void loadSettings() {
        UserApiSettingsDto settings = credentialsService.getSettings();
        meritApiId.setValue(nullToEmpty(settings.meritApiId()));
        meritApiKey.clear();
        meritApiKey.setPlaceholder(settings.meritApiKeySet() ? "•••••••• (zapisany)" : "");
    }

    private void loadAllegroAccounts() {
        List<AllegroAccountDto> accounts = allegroAccountService.listAccounts();
        allegroAccountsGrid.setItems(accounts);
    }

    private void saveMerit() {
        try {
            credentialsService.saveMeritCredentials(meritApiId.getValue(), meritApiKey.getValue());
            Notifications.show("Zapisano ustawienia Merit.", NotificationVariant.SUCCESS);
            loadSettings();
        } catch (ResponseStatusException ex) {
            Notifications.show(reason(ex), NotificationVariant.ERROR);
        } catch (RuntimeException ex) {
            Notifications.show("Nie udało się zapisać ustawień Merit.", NotificationVariant.ERROR);
        }
    }

    private void addAllegroAccount() {
        try {
            allegroAccountService.addAccount(
                    allegroName.getValue(), allegroClientId.getValue(), allegroClientSecret.getValue());
            Notifications.show("Dodano konto Allegro.", NotificationVariant.SUCCESS);
            allegroName.clear();
            allegroClientId.clear();
            allegroClientSecret.clear();
            loadAllegroAccounts();
        } catch (ResponseStatusException ex) {
            Notifications.show(reason(ex), NotificationVariant.ERROR);
        } catch (RuntimeException ex) {
            Notifications.show("Nie udało się dodać konta Allegro.", NotificationVariant.ERROR);
        }
    }

    private void connectAllegro(Long accountId) {
        try {
            UI.getCurrent().getPage().setLocation(allegroAuthService.buildAuthorizationUrl(accountId));
        } catch (ResponseStatusException ex) {
            Notifications.show(reason(ex), NotificationVariant.ERROR);
        } catch (RuntimeException ex) {
            Notifications.show("Nie udało się rozpocząć połączenia z Allegro.", NotificationVariant.ERROR);
        }
    }

    private void deleteAllegroAccount(Long accountId) {
        try {
            allegroAccountService.deleteAccount(accountId);
            Notifications.show("Usunięto konto Allegro.", NotificationVariant.SUCCESS);
            loadAllegroAccounts();
        } catch (ResponseStatusException ex) {
            Notifications.show(reason(ex), NotificationVariant.ERROR);
        } catch (RuntimeException ex) {
            Notifications.show("Nie udało się usunąć konta Allegro.", NotificationVariant.ERROR);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String reason(ResponseStatusException ex) {
        if (ex.getReason() == null || ex.getReason().isBlank()) {
            return "Operacja nie powiodła się.";
        }
        return ex.getReason();
    }
}
