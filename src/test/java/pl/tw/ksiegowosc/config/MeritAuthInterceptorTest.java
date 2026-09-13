package pl.tw.ksiegowosc.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;

import org.junit.jupiter.api.Test;

import pl.tw.ksiegowosc.dto.MeritCredentials;

class MeritAuthInterceptorTest {

    @Test
    void shouldCreateDeterministicHmacSignature() {
        MeritAuthInterceptor interceptor = new MeritAuthInterceptor(
                () -> new MeritCredentials("test-api-id", "test-api-key"),
                Clock.systemUTC());

        String first = interceptor.sign("20260818100000", "{\"PeriodStart\":\"20260817\"}");
        String second = interceptor.sign("20260818100000", "{\"PeriodStart\":\"20260817\"}");
        String otherBody = interceptor.sign("20260818100000", "{\"PeriodStart\":\"20260816\"}");

        assertThat(first).isEqualTo(second);
        assertThat(first).isNotBlank();
        assertThat(first).isNotEqualTo(otherBody);
    }
}
