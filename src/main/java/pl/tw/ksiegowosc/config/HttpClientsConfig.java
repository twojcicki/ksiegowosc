package pl.tw.ksiegowosc.config;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({MeritApiProperties.class, AllegroApiProperties.class})
public class HttpClientsConfig {

    public static final String ALLEGRO_ACCEPT = "application/vnd.allegro.public.v1+json";

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    MeritAuthInterceptor meritAuthInterceptor(
            pl.tw.ksiegowosc.service.CurrentUserApiCredentialsService credentialsService,
            Clock clock) {
        return new MeritAuthInterceptor(credentialsService::requireMeritCredentials, clock);
    }

    @Bean
    MeritLoggingInterceptor meritLoggingInterceptor() {
        return new MeritLoggingInterceptor();
    }

    @Bean
    RestClient meritRestClient(
            RestClient.Builder builder,
            MeritApiProperties properties,
            MeritAuthInterceptor interceptor,
            MeritLoggingInterceptor loggingInterceptor) {
        return builder
                .baseUrl(properties.baseUrl())
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .requestInterceptor(loggingInterceptor)
                .requestInterceptor(interceptor)
                .build();
    }

    @Bean
    RestClient meritV2RestClient(
            MeritApiProperties properties,
            MeritAuthInterceptor interceptor,
            MeritLoggingInterceptor loggingInterceptor) {
        return RestClient.builder()
                .baseUrl(properties.v2BaseUrl())
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .requestInterceptor(loggingInterceptor)
                .requestInterceptor(interceptor)
                .build();
    }

    @Bean
    RestClient allegroAuthRestClient(AllegroApiProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.authUrl())
                .build();
    }

    @Bean
    RestClient allegroRestClient(AllegroApiProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.apiBaseUrl())
                .defaultHeader("Accept", ALLEGRO_ACCEPT)
                .build();
    }
}
