package pl.tw.ksiegowosc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.router.PageTitle;

@SpringBootApplication
@PageTitle("Ksiegowosc")
public class KsiegowoscApplication implements AppShellConfigurator {

    public static void main(String[] args) {
        SpringApplication.run(KsiegowoscApplication.class, args);
    }
}
