package pl.tw.ksiegowosc.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import pl.tw.ksiegowosc.client.MeritApiClient;
import pl.tw.ksiegowosc.dto.SalesInvoiceDetailsDto;
import pl.tw.ksiegowosc.dto.SalesInvoiceDto;

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
}
