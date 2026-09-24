package pl.tw.ksiegowosc.ui;

import java.util.List;
import java.util.Optional;

import org.springframework.web.server.ResponseStatusException;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.QueryParameters;
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
public class ApiSettingsView extends View implements BeforeEnterObserver {

    private final CurrentUserApiCredentialsService credentialsService;
    private final AllegroAccountService allegroAccountService;
    private final AllegroAuthService allegroAuthService;

    private final TextField meritApiId = new TextField("Merit Api Id");
    private final PasswordField meritApiKey = new PasswordField("Merit Api Key");
    private final TextField allegroName = new TextField("Nazwa konta");
    private final TextField allegroClientId = new TextField("Allegro Client ID");
    private final PasswordField allegroClientSecret = new PasswordField("Allegro Client Secret");
    private final TextField allegroInvoicePrefix = new TextField("Prefiks faktury");
    private final TextField allegroApiBaseUrl = new TextField("API Base URL");
    private final TextField allegroAuthUrl = new TextField("Auth URL");
    private final TextField allegroUserAgent = new TextField("User-Agent");
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

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        QueryParameters query = event.getLocation().getQueryParameters();
        Optional<String> oauthError = query.getSingleParameter("allegro_oauth_error");
        if (oauthError.isPresent() && !oauthError.get().isBlank()) {
            Notification notification = Notification.show(
                    oauthError.get(), 12_000, Notification.Position.MIDDLE);
            notification.addThemeVariants(NotificationVariant.ERROR);
            event.forwardTo(ApiSettingsView.class);
        }
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
        allegroInvoicePrefix.setWidthFull();
        allegroInvoicePrefix.setMaxLength(20);
        allegroInvoicePrefix.setHelperText("Numer faktury: prefiks/kolejny/MM/rrrr, np. FS/5/09/2026");
        allegroApiBaseUrl.setWidthFull();
        allegroApiBaseUrl.setValue(AllegroAccountService.DEFAULT_API_BASE_URL);
        allegroApiBaseUrl.setHelperText(
                "Produkcja: https://api.allegro.pl · Sandbox: https://api.allegro.pl.allegrosandbox.pl");
        allegroAuthUrl.setWidthFull();
        allegroAuthUrl.setValue(AllegroAccountService.DEFAULT_AUTH_URL);
        allegroAuthUrl.setHelperText(
                "Produkcja: https://allegro.pl · Sandbox: https://allegro.pl.allegrosandbox.pl");
        allegroUserAgent.setWidthFull();
        allegroUserAgent.setValue(AllegroAccountService.DEFAULT_USER_AGENT);
        allegroUserAgent.setHelperText("Format: NazwaAplikacji/Wersja (+https://url) — nazwa = aplikacja w Allegro.");

        Button addAllegro = new Button("Dodaj konto", event -> addAllegroAccount());
        addAllegro.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Span redirectUriHint = new Span();
        redirectUriHint.getStyle().set("font-size", "var(--lumo-font-size-s)");
        redirectUriHint.getStyle().set("word-break", "break-all");
        try {
            redirectUriHint.setText("Redirect URI (wpisz dokładnie to samo w Allegro Developer Apps): "
                    + allegroAuthService.resolveRedirectUri());
        } catch (RuntimeException ex) {
            redirectUriHint.setText("Redirect URI: ustaw ALLEGRO_REDIRECT_URI na Render.");
        }

        FormLayout allegroForm = new FormLayout(
                allegroName,
                allegroClientId,
                allegroClientSecret,
                allegroInvoicePrefix,
                allegroApiBaseUrl,
                allegroAuthUrl,
                allegroUserAgent);
        allegroForm.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
        VerticalLayout allegroSection = new VerticalLayout(
                new H3("Allegro"),
                new Paragraph(
                        "Dodaj aplikacje Allegro (Client ID/Secret, URL-e, User-Agent i prefiks). Połącz każde konto osobno."),
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
        allegroAccountsGrid.addColumn(AllegroAccountDto::invoicePrefix)
                .setHeader("Prefiks")
                .setAutoWidth(true)
                .setFlexGrow(0);
        allegroAccountsGrid.addColumn(AllegroAccountDto::apiBaseUrl).setHeader("API Base URL").setFlexGrow(1);
        allegroAccountsGrid.addColumn(AllegroAccountDto::authUrl).setHeader("Auth URL").setFlexGrow(1);
        allegroAccountsGrid.addColumn(AllegroAccountDto::userAgent).setHeader("User-Agent").setFlexGrow(1);
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
                    Button edit = new Button("Edytuj", e -> openEditAccountDialog(account));
                    edit.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
                    Button connect = new Button("Połącz", e -> connectAllegro(account.id()));
                    connect.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY);
                    Button remove = new Button("Usuń", e -> deleteAllegroAccount(account.id()));
                    remove.addThemeVariants(
                            ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
                    HorizontalLayout actions = new HorizontalLayout(edit, connect, remove);
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
                    allegroName.getValue(),
                    allegroClientId.getValue(),
                    allegroClientSecret.getValue(),
                    allegroInvoicePrefix.getValue(),
                    allegroApiBaseUrl.getValue(),
                    allegroAuthUrl.getValue(),
                    allegroUserAgent.getValue());
            Notifications.show("Dodano konto Allegro.", NotificationVariant.SUCCESS);
            allegroName.clear();
            allegroClientId.clear();
            allegroClientSecret.clear();
            allegroInvoicePrefix.clear();
            allegroApiBaseUrl.setValue(AllegroAccountService.DEFAULT_API_BASE_URL);
            allegroAuthUrl.setValue(AllegroAccountService.DEFAULT_AUTH_URL);
            allegroUserAgent.setValue(AllegroAccountService.DEFAULT_USER_AGENT);
            loadAllegroAccounts();
        } catch (ResponseStatusException ex) {
            Notifications.show(reason(ex), NotificationVariant.ERROR);
        } catch (RuntimeException ex) {
            Notifications.show("Nie udało się dodać konta Allegro.", NotificationVariant.ERROR);
        }
    }

    private void openEditAccountDialog(AllegroAccountDto account) {
        TextField nameField = new TextField("Nazwa konta");
        nameField.setWidthFull();
        nameField.setValue(nullToEmpty(account.name()));

        TextField clientIdField = new TextField("Allegro Client ID");
        clientIdField.setWidthFull();
        clientIdField.setValue(nullToEmpty(account.clientId()));
        clientIdField.setReadOnly(true);

        TextField prefixField = new TextField("Prefiks faktury");
        prefixField.setWidthFull();
        prefixField.setMaxLength(20);
        prefixField.setValue(nullToEmpty(account.invoicePrefix()));
        prefixField.setHelperText("Numer faktury: prefiks/kolejny/MM/rrrr, np. FS/5/09/2026");

        TextField apiBaseUrlField = new TextField("API Base URL");
        apiBaseUrlField.setWidthFull();
        apiBaseUrlField.setValue(nullToEmpty(account.apiBaseUrl()));
        apiBaseUrlField.setHelperText(
                "Produkcja: https://api.allegro.pl · Sandbox: https://api.allegro.pl.allegrosandbox.pl");

        TextField authUrlField = new TextField("Auth URL");
        authUrlField.setWidthFull();
        authUrlField.setValue(nullToEmpty(account.authUrl()));
        authUrlField.setHelperText(
                "Produkcja: https://allegro.pl · Sandbox: https://allegro.pl.allegrosandbox.pl");

        TextField userAgentField = new TextField("User-Agent");
        userAgentField.setWidthFull();
        userAgentField.setValue(nullToEmpty(account.userAgent()));
        userAgentField.setHelperText("Format: NazwaAplikacji/Wersja (+https://url) — nazwa = aplikacja w Allegro.");

        FormLayout form = new FormLayout(
                nameField, clientIdField, prefixField, apiBaseUrlField, authUrlField, userAgentField);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
        form.setWidthFull();

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Edytuj konto Allegro");
        dialog.setWidth("480px");
        dialog.add(form);

        Button cancel = new Button("Anuluj", e -> dialog.close());
        Button save = new Button("Zapisz", e -> {
            try {
                allegroAccountService.updateAccount(
                        account.id(),
                        nameField.getValue(),
                        prefixField.getValue(),
                        apiBaseUrlField.getValue(),
                        authUrlField.getValue(),
                        userAgentField.getValue());
                Notifications.show("Zapisano konto Allegro.", NotificationVariant.SUCCESS);
                dialog.close();
                loadAllegroAccounts();
            } catch (ResponseStatusException ex) {
                Notifications.show(reason(ex), NotificationVariant.ERROR);
            } catch (RuntimeException ex) {
                Notifications.show("Nie udało się zapisać konta Allegro.", NotificationVariant.ERROR);
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(cancel, save);
        dialog.open();
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
