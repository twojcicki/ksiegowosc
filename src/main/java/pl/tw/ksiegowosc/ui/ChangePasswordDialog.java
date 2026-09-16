package pl.tw.ksiegowosc.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.PasswordField;

import org.springframework.web.server.ResponseStatusException;

import pl.tw.ksiegowosc.service.AppUserService;
import pl.tw.ksiegowosc.ui.util.Notifications;

public class ChangePasswordDialog extends Dialog {

    private final AppUserService appUserService;

    private final PasswordField currentPassword = new PasswordField("Obecne hasło");
    private final PasswordField newPassword = new PasswordField("Nowe hasło");
    private final PasswordField confirmPassword = new PasswordField("Potwierdź nowe hasło");

    public ChangePasswordDialog(AppUserService appUserService) {
        this.appUserService = appUserService;

        setHeaderTitle("Zmień hasło");
        setWidth("420px");
        setMaxWidth("95vw");
        setCloseOnEsc(true);
        setCloseOnOutsideClick(false);

        currentPassword.setWidthFull();
        currentPassword.setRequired(true);
        newPassword.setWidthFull();
        newPassword.setRequired(true);
        newPassword.setHelperText("Minimum 8 znaków.");
        confirmPassword.setWidthFull();
        confirmPassword.setRequired(true);

        FormLayout form = new FormLayout(currentPassword, newPassword, confirmPassword);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
        add(form);

        Button cancelButton = new Button("Anuluj", event -> close());
        Button saveButton = new Button("Zapisz", event -> save());
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout footer = new HorizontalLayout(cancelButton, saveButton);
        footer.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        footer.setWidthFull();
        getFooter().add(footer);
    }

    private void save() {
        try {
            appUserService.changePassword(
                    currentPassword.getValue(),
                    newPassword.getValue(),
                    confirmPassword.getValue());
            Notifications.show("Hasło zostało zmienione.", NotificationVariant.SUCCESS);
            close();
        } catch (ResponseStatusException ex) {
            Notifications.show(reason(ex), NotificationVariant.ERROR);
        } catch (RuntimeException ex) {
            Notifications.show("Nie udało się zmienić hasła.", NotificationVariant.ERROR);
        }
    }

    private static String reason(ResponseStatusException ex) {
        String reason = ex.getReason();
        return reason == null || reason.isBlank() ? "Nie udało się zmienić hasła." : reason;
    }
}
