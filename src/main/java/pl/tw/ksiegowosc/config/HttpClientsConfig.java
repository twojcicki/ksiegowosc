package pl.tw.ksiegowosc.config;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(MeritApiProperties.class)
public class HttpClientsConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    MeritAuthInterceptor meritAuthInterceptor(MeritApiProperties properties, Clock clock) {
        return new MeritAuthInterceptor(properties, clock);
    }

    @Bean
    RestClient meritRestClient(
            RestClient.Builder builder,
            MeritApiProperties properties,
            MeritAuthInterceptor interceptor) {
        return builder
                .baseUrl(properties.baseUrl())
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .requestInterceptor(interceptor)
                .build();
    }
}
