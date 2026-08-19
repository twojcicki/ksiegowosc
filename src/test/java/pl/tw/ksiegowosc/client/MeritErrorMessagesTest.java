package pl.tw.ksiegowosc.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

class MeritErrorMessagesTest {

    @Test
    void shouldReadMessageFieldFromJsonBody() {
        HttpClientErrorException ex = HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST,
                "Bad Request",
                HttpHeaders.EMPTY,
                "{\"Message\":\"E-mail nadawcy nie został wpisany w ustawieniach faktury sprzedaży.\"}"
                        .getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);

        assertThat(MeritErrorMessages.from(ex))
                .isEqualTo("E-mail nadawcy nie został wpisany w ustawieniach faktury sprzedaży.");
    }
}
