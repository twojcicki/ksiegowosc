package pl.tw.ksiegowosc.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import pl.tw.ksiegowosc.client.MeritApiClient;
import pl.tw.ksiegowosc.client.MeritErrorMessages;
import pl.tw.ksiegowosc.dto.CreateInvoiceRequest;
import pl.tw.ksiegowosc.dto.CreateInvoiceResponse;
import pl.tw.ksiegowosc.dto.MeritCreateInvoiceRequest;
import pl.tw.ksiegowosc.dto.MeritCreateInvoiceResponse;
import pl.tw.ksiegowosc.dto.SalesInvoiceDetailsDto;
import pl.tw.ksiegowosc.dto.SalesInvoiceDto;
import pl.tw.ksiegowosc.dto.SendInvoiceEmailResponse;
import pl.tw.ksiegowosc.entity.InvoiceEmailStatus;
import pl.tw.ksiegowosc.mapper.AllegroInvoiceMappingSupport;
import pl.tw.ksiegowosc.mapper.MeritInvoiceMapper;
import pl.tw.ksiegowosc.repository.InvoiceEmailStatusRepository;

@Service
public class InvoicesService {

    private final MeritApiClient meritApiClient;
    private final InvoiceEmailStatusRepository invoiceEmailStatusRepository;
    private final MeritInvoiceMapper meritInvoiceMapper;

    public InvoicesService(
            MeritApiClient meritApiClient,
            InvoiceEmailStatusRepository invoiceEmailStatusRepository,
            MeritInvoiceMapper meritInvoiceMapper) {
        this.meritApiClient = meritApiClient;
        this.invoiceEmailStatusRepository = invoiceEmailStatusRepository;
        this.meritInvoiceMapper = meritInvoiceMapper;
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
        List<SalesInvoiceDto> invoices = meritApiClient.getInvoices(from, to);
        return enrichWithEmailStatus(invoices);
    }

    public SalesInvoiceDetailsDto getInvoiceDetails(String id, boolean addAttachment) {
        SalesInvoiceDetailsDto details = meritApiClient.getInvoiceDetails(id, addAttachment);
        if (details == null || details.header() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleziono faktury o podanym identyfikatorze.");
        }
        return details;
    }

    public CreateInvoiceResponse createInvoice(CreateInvoiceRequest request) {
        MeritCreateInvoiceRequest meritRequest = meritInvoiceMapper.toMeritRequest(request);
        try {
            MeritCreateInvoiceResponse meritResponse = meritApiClient.createInvoice(meritRequest);
            if (meritResponse == null
                    || meritResponse.invoiceId() == null
                    || meritResponse.invoiceId().isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Merit nie zwrócił identyfikatora utworzonej faktury.");
            }
            return new CreateInvoiceResponse(meritResponse.invoiceId(), meritResponse.customerId());
        } catch (RestClientResponseException ex) {
            HttpStatusCode status = ex.getStatusCode().is4xxClientError()
                    ? ex.getStatusCode()
                    : HttpStatus.BAD_GATEWAY;
            throw new ResponseStatusException(status, MeritErrorMessages.from(ex), ex);
        }
    }

    public String nextInvoiceNoFromMerit(String prefix, LocalDate docDate) {
        if (prefix == null || prefix.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Podaj prefiks faktury.");
        }
        if (docDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Podaj datę dokumentu faktury.");
        }
        LocalDate from = docDate.withDayOfMonth(1);
        LocalDate to = docDate.withDayOfMonth(docDate.lengthOfMonth());
        List<SalesInvoiceDto> invoices;
        try {
            invoices = meritApiClient.getInvoices(from, to);
        } catch (RestClientResponseException ex) {
            HttpStatusCode status = ex.getStatusCode().is4xxClientError()
                    ? ex.getStatusCode()
                    : HttpStatus.BAD_GATEWAY;
            throw new ResponseStatusException(status, MeritErrorMessages.from(ex), ex);
        }
        int count = invoices == null ? 0 : invoices.size();
        int next = AllegroInvoiceMappingSupport.nextSequenceNumber(count);
        try {
            return AllegroInvoiceMappingSupport.buildInvoiceNo(prefix.trim(), next, docDate);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nie udało się zbudować numeru faktury.", ex);
        }
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
            markEmailSent(id);
            return new SendInvoiceEmailResponse("OK");
        }
        String message = (result == null || result.isBlank())
                ? "Nie udało się wysłać faktury e-mailem."
                : result.trim();
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, message);
    }

    private List<SalesInvoiceDto> enrichWithEmailStatus(List<SalesInvoiceDto> invoices) {
        if (invoices == null || invoices.isEmpty()) {
            return invoices == null ? List.of() : invoices;
        }
        List<String> invoiceIds = invoices.stream()
                .map(SalesInvoiceDto::sihId)
                .filter(Objects::nonNull)
                .filter(id -> !id.isBlank())
                .toList();
        if (invoiceIds.isEmpty()) {
            return invoices;
        }
        Map<String, InvoiceEmailStatus> statuses = invoiceEmailStatusRepository.findAllByInvoiceIdIn(invoiceIds)
                .stream()
                .collect(Collectors.toMap(InvoiceEmailStatus::getInvoiceId, Function.identity()));
        return invoices.stream()
                .map(invoice -> withEmailStatus(invoice, statuses.get(invoice.sihId())))
                .toList();
    }

    private static SalesInvoiceDto withEmailStatus(SalesInvoiceDto invoice, InvoiceEmailStatus status) {
        if (status == null) {
            return new SalesInvoiceDto(
                    invoice.sihId(),
                    invoice.invoiceNo(),
                    invoice.documentDate(),
                    invoice.customerName(),
                    invoice.totalAmount(),
                    invoice.paid(),
                    false,
                    null);
        }
        return new SalesInvoiceDto(
                invoice.sihId(),
                invoice.invoiceNo(),
                invoice.documentDate(),
                invoice.customerName(),
                invoice.totalAmount(),
                invoice.paid(),
                status.isEmailSent(),
                status.getEmailSentAt());
    }

    private void markEmailSent(String invoiceId) {
        InvoiceEmailStatus status = invoiceEmailStatusRepository.findById(invoiceId)
                .orElseGet(() -> {
                    InvoiceEmailStatus created = new InvoiceEmailStatus();
                    created.setInvoiceId(invoiceId);
                    return created;
                });
        status.setEmailSent(true);
        status.setEmailSentAt(Instant.now());
        invoiceEmailStatusRepository.save(status);
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
