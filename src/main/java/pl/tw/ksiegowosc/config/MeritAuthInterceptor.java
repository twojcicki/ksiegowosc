package pl.tw.ksiegowosc.config;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.function.Supplier;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.support.HttpRequestWrapper;
import org.springframework.web.util.UriComponentsBuilder;

import pl.tw.ksiegowosc.dto.MeritCredentials;

public class MeritAuthInterceptor implements ClientHttpRequestInterceptor {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);

    private final Supplier<MeritCredentials> credentialsSupplier;
    private final Clock clock;

    public MeritAuthInterceptor(Supplier<MeritCredentials> credentialsSupplier, Clock clock) {
        this.credentialsSupplier = credentialsSupplier;
        this.clock = clock;
    }

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution) throws IOException {
        MeritCredentials credentials = credentialsSupplier.get();
        byte[] payload = body != null ? body : new byte[0];
        String timestamp = TIMESTAMP_FORMAT.format(clock.instant());
        String httpBody = new String(payload, StandardCharsets.UTF_8);
        String signature = sign(credentials.apiId(), credentials.apiKey(), timestamp, httpBody);

        URI signedUri = UriComponentsBuilder.fromUri(request.getURI())
                .queryParam("apiId", "{apiId}")
                .queryParam("timestamp", "{timestamp}")
                .queryParam("signature", "{signature}")
                .encode()
                .buildAndExpand(credentials.apiId(), timestamp, signature)
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
        MeritCredentials credentials = credentialsSupplier.get();
        return sign(credentials.apiId(), credentials.apiKey(), timestamp, httpBody);
    }

    public static String sign(String apiId, String apiKey, String timestamp, String httpBody) {
        String dataToSign = apiId + timestamp + httpBody;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(apiKey.getBytes(StandardCharsets.US_ASCII), "HmacSHA256"));
            byte[] signatureBytes = mac.doFinal(dataToSign.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signatureBytes);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to sign Merit API request", ex);
        }
    }
}
