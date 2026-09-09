package pl.tw.ksiegowosc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.server.AppShellSettings;
import com.vaadin.flow.theme.aura.Aura;

@SpringBootApplication
@PageTitle("Księgowość")
@StyleSheet(Aura.STYLESHEET)
@StyleSheet("styles.css")
public class KsiegowoscApplication implements AppShellConfigurator {

    public static void main(String[] args) {
        SpringApplication.run(KsiegowoscApplication.class, args);
    }

    @Override
    public void configurePage(AppShellSettings settings) {
        settings.addFavIcon("icon", "favicon.svg", "32x32");
    }
}
