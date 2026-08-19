package pl.tw.ksiegowosc.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;

import org.junit.jupiter.api.Test;

class MeritAuthInterceptorTest {

    @Test
    void shouldCreateDeterministicHmacSignature() {
        MeritApiProperties properties = new MeritApiProperties(
                "https://program.360ksiegowosc.pl/api/v1",
                "test-api-id",
                "test-api-key",
                "https://program.360ksiegowosc.pl/api/v2");
        MeritAuthInterceptor interceptor = new MeritAuthInterceptor(properties, Clock.systemUTC());

        String first = interceptor.sign("20260818100000", "{\"PeriodStart\":\"20260817\"}");
        String second = interceptor.sign("20260818100000", "{\"PeriodStart\":\"20260817\"}");
        String otherBody = interceptor.sign("20260818100000", "{\"PeriodStart\":\"20260816\"}");

        assertThat(first).isEqualTo(second);
        assertThat(first).isNotBlank();
        assertThat(first).isNotEqualTo(otherBody);
    }
}
