package pl.tw.ksiegowosc.config;

import java.io.IOException;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import pl.tw.ksiegowosc.service.AllegroAuthService;

public class AllegroAuthInterceptor implements ClientHttpRequestInterceptor {

    private final AllegroAuthService authService;

    public AllegroAuthInterceptor(AllegroAuthService authService) {
        this.authService = authService;
    }

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution) throws IOException {
        request.getHeaders().setBearerAuth(authService.getValidAccessToken());
        return execution.execute(request, body);
    }
}
