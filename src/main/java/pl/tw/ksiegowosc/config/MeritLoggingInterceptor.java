package pl.tw.ksiegowosc.config;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

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
    private static final int MAX_BODY_CHARS = 32_000;

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution) throws IOException {
        String requestBody = body == null || body.length == 0
                ? "<empty>"
                : truncate(new String(body, StandardCharsets.UTF_8));
        log.info("Merit API request {} {} body={}", request.getMethod(), safeUri(request.getURI()), requestBody);

        ClientHttpResponse response = execution.execute(request, body);
        BufferingClientHttpResponse buffered = new BufferingClientHttpResponse(response);
        String responseBody = truncate(buffered.bodyAsString());
        log.info(
                "Merit API response {} {} -> {} body={}",
                request.getMethod(),
                safeUri(request.getURI()),
                buffered.getStatusCode().value(),
                responseBody);
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

    private static String truncate(String value) {
        if (value.length() <= MAX_BODY_CHARS) {
            return value;
        }
        return value.substring(0, MAX_BODY_CHARS) + "... [truncated " + (value.length() - MAX_BODY_CHARS) + " chars]";
    }

    private static final class BufferingClientHttpResponse implements ClientHttpResponse {

        private final ClientHttpResponse delegate;
        private final byte[] body;

        private BufferingClientHttpResponse(ClientHttpResponse delegate) throws IOException {
            this.delegate = delegate;
            this.body = StreamUtils.copyToByteArray(delegate.getBody());
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
