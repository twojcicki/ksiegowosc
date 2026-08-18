package pl.tw.ksiegowosc.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI ksiegowoscOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Ksiegowosc API")
                        .version("0.0.1-SNAPSHOT")
                        .description("Szkielet Spring Boot 4 z klientem Merit Aktiva do pobierania faktur sprzedaży."));
    }
}
