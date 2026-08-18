package pl.tw.ksiegowosc.config;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.support.HttpRequestWrapper;
import org.springframework.web.util.UriComponentsBuilder;

public class MeritAuthInterceptor implements ClientHttpRequestInterceptor {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);

    private final MeritApiProperties properties;
    private final Clock clock;

    public MeritAuthInterceptor(MeritApiProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution) throws IOException {
        byte[] payload = body != null ? body : new byte[0];
        String timestamp = TIMESTAMP_FORMAT.format(clock.instant());
        String httpBody = new String(payload, StandardCharsets.UTF_8);
        String signature = sign(timestamp, httpBody);

        URI signedUri = UriComponentsBuilder.fromUri(request.getURI())
                .queryParam("apiId", properties.apiId())
                .queryParam("timestamp", timestamp)
                .queryParam("signature", signature)
                .build()
                .encode()
                .toUri();

        HttpRequest signedRequest = new HttpRequestWrapper(request) {
            @Override
            public URI getURI() {
                return signedUri;
            }
        };

        return execution.execute(signedRequest, payload);
    }

    public String sign(String timestamp, String httpBody) {
        String dataToSign = properties.apiId() + timestamp + httpBody;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.apiKey().getBytes(StandardCharsets.US_ASCII), "HmacSHA256"));
            byte[] signatureBytes = mac.doFinal(dataToSign.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signatureBytes);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to sign Merit API request", ex);
        }
    }
}
