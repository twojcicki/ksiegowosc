package pl.tw.ksiegowosc.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import pl.tw.ksiegowosc.client.MeritErrorMessages;
import pl.tw.ksiegowosc.dto.ApiErrorResponse;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatus(ResponseStatusException ex) {
        int status = ex.getStatusCode().value();
        String message = ex.getReason() == null || ex.getReason().isBlank()
                ? ex.getStatusCode().toString()
                : ex.getReason();
        return ResponseEntity.status(status).body(new ApiErrorResponse(status, message));
    }

    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<ApiErrorResponse> handleRestClient(RestClientResponseException ex) {
        int status = ex.getStatusCode().is4xxClientError()
                ? ex.getStatusCode().value()
                : HttpStatus.BAD_GATEWAY.value();
        return ResponseEntity.status(status).body(new ApiErrorResponse(status, MeritErrorMessages.from(ex)));
    }
}
