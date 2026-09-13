package pl.tw.ksiegowosc.ui;

import org.springframework.web.server.ResponseStatusException;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.PermitAll;
import pl.tw.ksiegowosc.dto.UserApiSettingsDto;
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
    private final AllegroAuthService allegroAuthService;

    private final TextField meritApiId = new TextField("Merit Api Id");
    private final PasswordField meritApiKey = new PasswordField("Merit Api Key");
    private final TextField allegroClientId = new TextField("Allegro Client ID");
    private final PasswordField allegroClientSecret = new PasswordField("Allegro Client Secret");
    private final Span allegroStatus = new Span();
    private final Button disconnectButton = new Button("Usuń powiązanie");

    public ApiSettingsView(
            CurrentUserApiCredentialsService credentialsService,
            AllegroAuthService allegroAuthService) {
        this.credentialsService = credentialsService;
        this.allegroAuthService = allegroAuthService;

        addClassNames(Aura.SURFACE_SOLID, "api-settings-view");
        add(createHeader(), createContent());
        loadSettings();
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
        allegroClientId.setWidthFull();
        allegroClientSecret.setWidthFull();
        allegroClientSecret.setHelperText("Pozostaw puste, aby nie zmieniać zapisanego secretu.");

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

        Button saveAllegro = new Button("Zapisz Allegro", event -> saveAllegro());
        saveAllegro.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button connectButton = new Button("Połącz z Allegro", event -> connectAllegro());

        disconnectButton.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
        disconnectButton.addClickListener(event -> disconnectAllegro());

        HorizontalLayout allegroActions = new HorizontalLayout(saveAllegro, connectButton, disconnectButton);
        allegroActions.setAlignItems(com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment.CENTER);

        FormLayout allegroForm = new FormLayout(allegroClientId, allegroClientSecret);
        allegroForm.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
        VerticalLayout allegroSection = new VerticalLayout(
                new H3("Allegro"),
                new Paragraph("Client ID i Client Secret aplikacji Allegro Sandbox. Redirect URI konfigurujesz w application.yml."),
                allegroForm,
                allegroStatus,
                allegroActions);
        allegroSection.setPadding(false);
        allegroSection.setSpacing(true);
        allegroSection.setWidthFull();

        VerticalLayout content = new VerticalLayout(meritSection, allegroSection);
        content.setPadding(true);
        content.setSpacing(true);
        content.setMaxWidth("640px");
        return content;
    }

    private void loadSettings() {
        UserApiSettingsDto settings = credentialsService.getSettings();
        meritApiId.setValue(nullToEmpty(settings.meritApiId()));
        meritApiKey.clear();
        meritApiKey.setPlaceholder(settings.meritApiKeySet() ? "•••••••• (zapisany)" : "");
        allegroClientId.setValue(nullToEmpty(settings.allegroClientId()));
        allegroClientSecret.clear();
        allegroClientSecret.setPlaceholder(settings.allegroClientSecretSet() ? "•••••••• (zapisany)" : "");
        updateAllegroStatus(settings.allegroConnected());
    }

    private void updateAllegroStatus(boolean connected) {
        allegroStatus.setText(connected ? "Status: połączono z Allegro" : "Status: brak powiązania z Allegro");
        disconnectButton.setEnabled(connected);
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

    private void saveAllegro() {
        try {
            credentialsService.saveAllegroCredentials(allegroClientId.getValue(), allegroClientSecret.getValue());
            Notifications.show("Zapisano ustawienia Allegro.", NotificationVariant.SUCCESS);
            loadSettings();
        } catch (ResponseStatusException ex) {
            Notifications.show(reason(ex), NotificationVariant.ERROR);
        } catch (RuntimeException ex) {
            Notifications.show("Nie udało się zapisać ustawień Allegro.", NotificationVariant.ERROR);
        }
    }

    private void connectAllegro() {
        try {
            UI.getCurrent().getPage().setLocation(allegroAuthService.buildAuthorizationUrl());
        } catch (ResponseStatusException ex) {
            Notifications.show(reason(ex), NotificationVariant.ERROR);
        } catch (RuntimeException ex) {
            Notifications.show("Nie udało się rozpocząć połączenia z Allegro.", NotificationVariant.ERROR);
        }
    }

    private void disconnectAllegro() {
        try {
            allegroAuthService.disconnect();
            Notifications.show("Usunięto powiązanie z Allegro.", NotificationVariant.SUCCESS);
            loadSettings();
        } catch (ResponseStatusException ex) {
            Notifications.show(reason(ex), NotificationVariant.ERROR);
        } catch (RuntimeException ex) {
            Notifications.show("Nie udało się usunąć powiązania Allegro.", NotificationVariant.ERROR);
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
