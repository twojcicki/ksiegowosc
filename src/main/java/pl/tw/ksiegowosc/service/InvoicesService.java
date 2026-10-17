package pl.tw.ksiegowosc.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
import pl.tw.ksiegowosc.dto.DeleteInvoiceResult;
import pl.tw.ksiegowosc.dto.MeritCreateInvoiceRequest;
import pl.tw.ksiegowosc.dto.MeritCreateInvoiceResponse;
import pl.tw.ksiegowosc.dto.MeritPaymentDto;
import pl.tw.ksiegowosc.dto.SalesInvoiceDetailsDto;
import pl.tw.ksiegowosc.dto.SalesInvoiceDto;
import pl.tw.ksiegowosc.dto.SalesInvoiceHeaderDto;
import pl.tw.ksiegowosc.dto.SalesInvoicePaymentDto;
import pl.tw.ksiegowosc.dto.SendInvoiceEmailResponse;
import pl.tw.ksiegowosc.entity.InvoiceEmailStatus;
import pl.tw.ksiegowosc.mapper.AllegroInvoiceMappingSupport;
import pl.tw.ksiegowosc.mapper.MeritInvoiceMapper;
import pl.tw.ksiegowosc.repository.InvoiceEmailStatusRepository;

@Service
public class InvoicesService {

    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

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

    public DeleteInvoiceResult deleteInvoice(String id) {
        if (id == null || id.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Podaj identyfikator faktury.");
        }
        String trimmedId = id.trim();
        SalesInvoiceDetailsDto details;
        try {
            details = meritApiClient.getInvoiceDetails(trimmedId, false);
        } catch (RestClientResponseException ex) {
            throw wrapMeritError(ex);
        }
        if (details == null || details.header() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleziono faktury o podanym identyfikatorze.");
        }

        SalesInvoiceHeaderDto header = details.header();
        String invoiceNo = blankToNull(header.invoiceNo()) != null ? header.invoiceNo().trim() : trimmedId;
        List<SalesInvoicePaymentDto> payments = details.payments() == null ? List.of() : details.payments();
        List<String> paymentIds = payments.stream()
                .map(SalesInvoicePaymentDto::paymentId)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();

        List<String> paymentDocumentNos = List.of();
        if (!paymentIds.isEmpty()) {
            paymentDocumentNos = resolvePaymentDocumentNos(paymentIds, payments, header.documentDate());
            try {
                for (String paymentId : paymentIds) {
                    meritApiClient.deletePayment(paymentId);
                }
            } catch (RestClientResponseException ex) {
                throw wrapMeritError(ex);
            }
        }

        try {
            meritApiClient.deleteInvoice(trimmedId);
        } catch (RestClientResponseException ex) {
            throw wrapMeritError(ex);
        }
        return new DeleteInvoiceResult(invoiceNo, paymentDocumentNos);
    }

    private List<String> resolvePaymentDocumentNos(
            List<String> paymentIds,
            List<SalesInvoicePaymentDto> payments,
            String documentDate) {
        Map<String, String> documentNosByPaymentId = Map.of();
        LocalDateRange range = paymentLookupRange(payments, documentDate);
        if (range != null) {
            try {
                List<MeritPaymentDto> meritPayments = meritApiClient.getPayments(range.from(), range.to());
                documentNosByPaymentId = indexPaymentDocumentNos(meritPayments, paymentIds);
            } catch (RestClientResponseException ex) {
                // DocumentNo is only for the success message — fall back to PaymentId.
            }
        }
        List<String> labels = new ArrayList<>(paymentIds.size());
        for (String paymentId : paymentIds) {
            String documentNo = documentNosByPaymentId.get(paymentId);
            labels.add(documentNo != null && !documentNo.isBlank() ? documentNo : paymentId);
        }
        return List.copyOf(labels);
    }

    private static Map<String, String> indexPaymentDocumentNos(
            List<MeritPaymentDto> meritPayments,
            List<String> paymentIds) {
        Set<String> wanted = new LinkedHashSet<>(paymentIds);
        Map<String, String> result = new HashMap<>();
        for (MeritPaymentDto payment : meritPayments) {
            if (payment == null) {
                continue;
            }
            String documentNo = blankToNull(payment.documentNo());
            if (documentNo == null) {
                continue;
            }
            if (payment.pihId() != null && wanted.contains(payment.pihId())) {
                result.putIfAbsent(payment.pihId(), documentNo);
            }
            if (payment.paymApiDetails() != null
                    && payment.paymApiDetails().paymId() != null
                    && wanted.contains(payment.paymApiDetails().paymId())) {
                result.putIfAbsent(payment.paymApiDetails().paymId(), documentNo);
            }
        }
        return result;
    }

    private static LocalDateRange paymentLookupRange(List<SalesInvoicePaymentDto> payments, String documentDate) {
        LocalDate min = null;
        LocalDate max = null;
        for (SalesInvoicePaymentDto payment : payments) {
            LocalDate paymDate = parseMeritDate(payment == null ? null : payment.paymDate());
            if (paymDate == null) {
                continue;
            }
            min = min == null || paymDate.isBefore(min) ? paymDate : min;
            max = max == null || paymDate.isAfter(max) ? paymDate : max;
        }
        if (min == null) {
            LocalDate invoiceDate = parseMeritDate(documentDate);
            if (invoiceDate == null) {
                return null;
            }
            min = invoiceDate;
            max = invoiceDate;
        }
        LocalDate from = min.minusDays(7);
        LocalDate to = max.plusDays(7);
        if (from.plusMonths(3).isBefore(to)) {
            to = from.plusMonths(3).minusDays(1);
        }
        return new LocalDateRange(from, to);
    }

    private static LocalDate parseMeritDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        try {
            if (trimmed.length() >= 10 && trimmed.charAt(4) == '-') {
                return LocalDate.parse(trimmed.substring(0, 10), ISO_DATE);
            }
            if (trimmed.length() >= 8) {
                return LocalDate.parse(trimmed.substring(0, 8), BASIC_DATE);
            }
        } catch (DateTimeParseException ignored) {
            return null;
        }
        return null;
    }

    private static ResponseStatusException wrapMeritError(RestClientResponseException ex) {
        HttpStatusCode status = ex.getStatusCode().is4xxClientError()
                ? ex.getStatusCode()
                : HttpStatus.BAD_GATEWAY;
        return new ResponseStatusException(status, MeritErrorMessages.from(ex), ex);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record LocalDateRange(LocalDate from, LocalDate to) {
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
                    invoice.taxAmount(),
                    invoice.roundingAmount(),
                    invoice.totalAmount(),
                    invoice.profitAmount(),
                    invoice.totalSum(),
                    invoice.paid(),
                    false,
                    null);
        }
        return new SalesInvoiceDto(
                invoice.sihId(),
                invoice.invoiceNo(),
                invoice.documentDate(),
                invoice.customerName(),
                invoice.taxAmount(),
                invoice.roundingAmount(),
                invoice.totalAmount(),
                invoice.profitAmount(),
                invoice.totalSum(),
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
