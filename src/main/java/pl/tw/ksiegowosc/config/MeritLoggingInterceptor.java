package pl.tw.ksiegowosc.config;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;

public class MeritLoggingInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(MeritLoggingInterceptor.class);
    static final int BODY_LOG_CHARS = 2_000;

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution) throws IOException {
        int requestBytes = body == null ? 0 : body.length;
        String method = String.valueOf(request.getMethod());
        String uri = safeUri(request.getURI());
        long started = System.nanoTime();

        ClientHttpResponse response = execution.execute(request, body);
        BufferingClientHttpResponse buffered = new BufferingClientHttpResponse(response);
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        int status = buffered.getStatusCode().value();
        int responseBytes = buffered.bodyLength();

        String summary = "Merit API %s %s -> %d (req=%db, res=%db) in %dms"
                .formatted(method, uri, status, requestBytes, responseBytes, elapsedMs);

        if (status >= 400) {
            log.warn("{} errorBody={}", summary, truncate(buffered.bodyAsString()));
        } else {
            log.info(summary);
        }

        if (log.isDebugEnabled()) {
            String requestBody = requestBytes == 0
                    ? "<empty>"
                    : truncate(new String(body, StandardCharsets.UTF_8));
            log.debug(
                    "Merit API {} {} debug bodies req={} res={}",
                    method,
                    uri,
                    requestBody,
                    truncate(buffered.bodyAsString()));
        }

        return buffered;
    }

    private static String safeUri(URI uri) {
        String path = uri.getPath();
        String query = uri.getRawQuery();
        if (query == null || query.isBlank()) {
            return path;
        }
        String redacted = query.replaceAll("(?i)(signature|apiId)=[^&]*", "$1=***");
        return path + "?" + redacted;
    }

    static String truncate(String value) {
        if (value == null) {
            return "<empty>";
        }
        if (value.length() <= BODY_LOG_CHARS) {
            return value;
        }
        return value.substring(0, BODY_LOG_CHARS) + "... [truncated " + (value.length() - BODY_LOG_CHARS) + " chars]";
    }

    private static final class BufferingClientHttpResponse implements ClientHttpResponse {

        private final ClientHttpResponse delegate;
        private final byte[] body;

        private BufferingClientHttpResponse(ClientHttpResponse delegate) throws IOException {
            this.delegate = delegate;
            this.body = StreamUtils.copyToByteArray(delegate.getBody());
        }

        private int bodyLength() {
            return body.length;
        }

        private String bodyAsString() {
            Charset charset = StandardCharsets.UTF_8;
            HttpHeaders headers = getHeaders();
            if (headers.getContentType() != null && headers.getContentType().getCharset() != null) {
                charset = headers.getContentType().getCharset();
            }
            if (body.length == 0) {
                return "<empty>";
            }
            return new String(body, charset);
        }

        @Override
        public HttpStatusCode getStatusCode() throws IOException {
            return delegate.getStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return delegate.getStatusText();
        }

        @Override
        public void close() {
            delegate.close();
        }

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(body);
        }

        @Override
        public HttpHeaders getHeaders() {
            return delegate.getHeaders();
        }
    }
}
