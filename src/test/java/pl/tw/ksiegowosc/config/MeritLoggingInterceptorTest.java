package pl.tw.ksiegowosc.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class MeritLoggingInterceptorTest {

    private MeritLoggingInterceptor interceptor;
    private ListAppender<ILoggingEvent> appender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        interceptor = new MeritLoggingInterceptor();
        logger = (Logger) org.slf4j.LoggerFactory.getLogger(MeritLoggingInterceptor.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    @Test
    void shouldLogMetadataWithoutBodyOnSuccess() throws Exception {
        byte[] requestBody = "{\"CustomerName\":\"Jan Kowalski\"}".getBytes(StandardCharsets.UTF_8);
        ClientHttpResponse response = mockResponse(HttpStatus.OK, "{\"InvoiceNo\":\"FS/1\"}");
        ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
        when(execution.execute(any(), eq(requestBody))).thenReturn(response);

        interceptor.intercept(request(HttpMethod.POST, "/api/v1/sendinvoice"), requestBody, execution);

        List<ILoggingEvent> events = appender.list;
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().getLevel()).isEqualTo(Level.INFO);
        String message = events.getFirst().getFormattedMessage();
        assertThat(message).contains("Merit API POST /api/v1/sendinvoice -> 200");
        assertThat(message).contains("req=" + requestBody.length + "b");
        assertThat(message).doesNotContain("Jan Kowalski");
        assertThat(message).doesNotContain("errorBody=");
        assertThat(message).doesNotContain("FS/1");
    }

    @Test
    void shouldLogTruncatedErrorBodyOnClientError() throws Exception {
        byte[] requestBody = "{}".getBytes(StandardCharsets.UTF_8);
        String longError = "E".repeat(MeritLoggingInterceptor.BODY_LOG_CHARS + 50);
        ClientHttpResponse response = mockResponse(HttpStatus.BAD_REQUEST, longError);
        ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
        when(execution.execute(any(), eq(requestBody))).thenReturn(response);

        interceptor.intercept(request(HttpMethod.POST, "/api/v1/sendinvoice"), requestBody, execution);

        List<ILoggingEvent> events = appender.list;
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().getLevel()).isEqualTo(Level.WARN);
        String message = events.getFirst().getFormattedMessage();
        assertThat(message).contains("-> 400");
        assertThat(message).contains("errorBody=");
        assertThat(message).contains("[truncated 50 chars]");
        assertThat(message).doesNotContain("E".repeat(MeritLoggingInterceptor.BODY_LOG_CHARS + 1));
    }

    @Test
    void shouldRedactSignatureAndApiIdInUri() throws Exception {
        byte[] requestBody = new byte[0];
        ClientHttpResponse response = mockResponse(HttpStatus.OK, "");
        ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
        when(execution.execute(any(), eq(requestBody))).thenReturn(response);

        interceptor.intercept(
                request(HttpMethod.GET, "/api/v1/getinvoices?apiId=secret&signature=sig123&foo=1"),
                requestBody,
                execution);

        String message = appender.list.getFirst().getFormattedMessage();
        assertThat(message).contains("apiId=***");
        assertThat(message).contains("signature=***");
        assertThat(message).contains("foo=1");
        assertThat(message).doesNotContain("secret");
        assertThat(message).doesNotContain("sig123");
    }

    @Test
    void truncateShouldCapLongValues() {
        String value = "x".repeat(MeritLoggingInterceptor.BODY_LOG_CHARS + 10);
        assertThat(MeritLoggingInterceptor.truncate(value))
                .hasSize(MeritLoggingInterceptor.BODY_LOG_CHARS + "... [truncated 10 chars]".length())
                .endsWith("[truncated 10 chars]");
    }

    private static HttpRequest request(HttpMethod method, String pathAndQuery) {
        HttpRequest request = mock(HttpRequest.class);
        when(request.getMethod()).thenReturn(method);
        when(request.getURI()).thenReturn(URI.create("https://merit.test" + pathAndQuery));
        return request;
    }

    private static ClientHttpResponse mockResponse(HttpStatus status, String body) throws Exception {
        ClientHttpResponse response = mock(ClientHttpResponse.class);
        when(response.getStatusCode()).thenReturn(status);
        when(response.getHeaders()).thenReturn(new HttpHeaders());
        when(response.getBody()).thenReturn(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
        return response;
    }
}
