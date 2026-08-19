package pl.tw.ksiegowosc.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import pl.tw.ksiegowosc.client.MeritApiClient;
import pl.tw.ksiegowosc.client.MeritErrorMessages;
import pl.tw.ksiegowosc.dto.SalesInvoiceDetailsDto;
import pl.tw.ksiegowosc.dto.SalesInvoiceDto;
import pl.tw.ksiegowosc.dto.SendInvoiceEmailResponse;

@Service
public class InvoicesService {

    private final MeritApiClient meritApiClient;

    public InvoicesService(MeritApiClient meritApiClient) {
        this.meritApiClient = meritApiClient;
    }

    public List<SalesInvoiceDto> getInvoices(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Data początkowa nie może być późniejsza niż data końcowa.");
        }
        if (from.plusMonths(3).isBefore(to)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Zakres dat nie może przekraczać 3 miesięcy.");
        }
        return meritApiClient.getInvoices(from, to);
    }

    public SalesInvoiceDetailsDto getInvoiceDetails(String id, boolean addAttachment) {
        SalesInvoiceDetailsDto details = meritApiClient.getInvoiceDetails(id, addAttachment);
        if (details == null || details.header() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleziono faktury o podanym identyfikatorze.");
        }
        return details;
    }

    public SendInvoiceEmailResponse sendInvoiceByEmail(String id, boolean delivNote) {
        String result;
        try {
            result = meritApiClient.sendInvoiceByEmail(id, delivNote);
        } catch (RestClientResponseException ex) {
            HttpStatusCode status = ex.getStatusCode().is4xxClientError()
                    ? ex.getStatusCode()
                    : HttpStatus.BAD_GATEWAY;
            throw new ResponseStatusException(status, MeritErrorMessages.from(ex), ex);
        }
        if (isOk(result)) {
            return new SendInvoiceEmailResponse("OK");
        }
        String message = (result == null || result.isBlank())
                ? "Nie udało się wysłać faktury e-mailem."
                : result.trim();
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, message);
    }

    private boolean isOk(String result) {
        if (result == null) {
            return false;
        }
        String normalized = result.trim();
        if (normalized.length() >= 2 && normalized.startsWith("\"") && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1).trim();
        }
        return "OK".equalsIgnoreCase(normalized);
    }
}
