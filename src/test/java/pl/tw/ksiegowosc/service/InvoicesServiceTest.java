package pl.tw.ksiegowosc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;

import pl.tw.ksiegowosc.client.MeritApiClient;
import pl.tw.ksiegowosc.dto.CreateInvoiceLineRequest;
import pl.tw.ksiegowosc.dto.CreateInvoiceRequest;
import pl.tw.ksiegowosc.dto.CreateInvoiceResponse;
import pl.tw.ksiegowosc.dto.CreateInvoiceTaxAmountRequest;
import pl.tw.ksiegowosc.dto.MeritCreateInvoiceRequest;
import pl.tw.ksiegowosc.dto.MeritCreateInvoiceResponse;
import pl.tw.ksiegowosc.dto.SalesInvoiceDetailsDto;
import pl.tw.ksiegowosc.dto.SalesInvoiceDto;
import pl.tw.ksiegowosc.dto.SalesInvoiceHeaderDto;
import pl.tw.ksiegowosc.dto.SendInvoiceEmailResponse;
import pl.tw.ksiegowosc.entity.InvoiceEmailStatus;
import pl.tw.ksiegowosc.mapper.MapperFixtures;
import pl.tw.ksiegowosc.repository.InvoiceEmailStatusRepository;

class InvoicesServiceTest {

    private MeritApiClient meritApiClient;
    private InvoiceEmailStatusRepository invoiceEmailStatusRepository;
    private InvoicesService invoicesService;

    @BeforeEach
    void setUp() {
        meritApiClient = mock(MeritApiClient.class);
        invoiceEmailStatusRepository = mock(InvoiceEmailStatusRepository.class);
        invoicesService = new InvoicesService(
                meritApiClient, invoiceEmailStatusRepository, MapperFixtures.meritInvoiceMapper());
    }

    @Test
    void shouldFetchInvoicesForValidPeriod() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 31);
        List<SalesInvoiceDto> meritInvoices = List.of(new SalesInvoiceDto(
                "id",
                "FV/1",
                "2026-01-15T00:00:00",
                "Klient",
                new BigDecimal("10.00"),
                false,
                null,
                null));
        when(meritApiClient.getInvoices(from, to)).thenReturn(meritInvoices);
        when(invoiceEmailStatusRepository.findAllByInvoiceIdIn(List.of("id"))).thenReturn(List.of());

        List<SalesInvoiceDto> invoices = invoicesService.getInvoices(from, to);

        assertThat(invoices).hasSize(1);
        assertThat(invoices.getFirst().emailSent()).isFalse();
        assertThat(invoices.getFirst().emailSentAt()).isNull();
        verify(meritApiClient).getInvoices(from, to);
    }

    @Test
    void shouldEnrichInvoicesWithEmailStatus() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 31);
        Instant sentAt = Instant.parse("2026-01-16T10:30:00Z");
        when(meritApiClient.getInvoices(from, to)).thenReturn(List.of(new SalesInvoiceDto(
                "id",
                "FV/1",
                "2026-01-15T00:00:00",
                "Klient",
                new BigDecimal("10.00"),
                false,
                null,
                null)));
        InvoiceEmailStatus status = new InvoiceEmailStatus();
        status.setInvoiceId("id");
        status.setEmailSent(true);
        status.setEmailSentAt(sentAt);
        when(invoiceEmailStatusRepository.findAllByInvoiceIdIn(List.of("id"))).thenReturn(List.of(status));

        List<SalesInvoiceDto> invoices = invoicesService.getInvoices(from, to);

        assertThat(invoices.getFirst().emailSent()).isTrue();
        assertThat(invoices.getFirst().emailSentAt()).isEqualTo(sentAt);
    }

    @Test
    void shouldRejectPeriodWhenFromIsAfterTo() {
        assertThatThrownBy(() -> invoicesService.getInvoices(
                LocalDate.of(2026, 1, 31),
                LocalDate.of(2026, 1, 1)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException statusEx = (ResponseStatusException) ex;
                    assertThat(statusEx.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(statusEx.getReason())
                            .isEqualTo("Data początkowa nie może być późniejsza niż data końcowa.");
                });
    }

    @Test
    void shouldRejectPeriodLongerThanThreeMonths() {
        assertThatThrownBy(() -> invoicesService.getInvoices(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 4, 2)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException statusEx = (ResponseStatusException) ex;
                    assertThat(statusEx.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(statusEx.getReason())
                            .isEqualTo("Zakres dat nie może przekraczać 3 miesięcy.");
                });
    }

    @Test
    void shouldAllowPeriodOfExactlyThreeMonths() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 4, 1);
        when(meritApiClient.getInvoices(from, to)).thenReturn(List.of());

        invoicesService.getInvoices(from, to);

        verify(meritApiClient).getInvoices(from, to);
    }

    @Test
    void shouldFetchInvoiceDetails() {
        String invoiceId = "5f91033c-9d0f-416e-a079-d3c892b8c317";
        SalesInvoiceDetailsDto expected = new SalesInvoiceDetailsDto(
                new SalesInvoiceHeaderDto(
                        invoiceId,
                        null, null, null, null, null,
                        "FV/1",
                        null, null, "Klient",
                        null, null, null, null, null, null, null,
                        new BigDecimal("10.00"),
                        null, null, null, null, null, null, null, null, null, null, null, null, null),
                List.of(),
                List.of(),
                null);
        when(meritApiClient.getInvoiceDetails(invoiceId, false)).thenReturn(expected);

        SalesInvoiceDetailsDto details = invoicesService.getInvoiceDetails(invoiceId, false);

        assertThat(details.header().invoiceNo()).isEqualTo("FV/1");
        verify(meritApiClient).getInvoiceDetails(invoiceId, false);
    }

    @Test
    void shouldReturnNotFoundWhenInvoiceDetailsAreMissing() {
        when(meritApiClient.getInvoiceDetails("missing-id", false)).thenReturn(null);

        assertThatThrownBy(() -> invoicesService.getInvoiceDetails("missing-id", false))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException statusEx = (ResponseStatusException) ex;
                    assertThat(statusEx.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(statusEx.getReason())
                            .isEqualTo("Nie znaleziono faktury o podanym identyfikatorze.");
                });
    }

    @Test
    void shouldSendInvoiceByEmail() {
        String invoiceId = "5f91033c-9d0f-416e-a079-d3c892b8c317";
        when(meritApiClient.sendInvoiceByEmail(invoiceId, false)).thenReturn("OK");
        when(invoiceEmailStatusRepository.findById(invoiceId)).thenReturn(Optional.empty());
        when(invoiceEmailStatusRepository.save(any(InvoiceEmailStatus.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SendInvoiceEmailResponse response = invoicesService.sendInvoiceByEmail(invoiceId, false);

        assertThat(response.status()).isEqualTo("OK");
        verify(meritApiClient).sendInvoiceByEmail(invoiceId, false);
        ArgumentCaptor<InvoiceEmailStatus> captor = ArgumentCaptor.forClass(InvoiceEmailStatus.class);
        verify(invoiceEmailStatusRepository).save(captor.capture());
        assertThat(captor.getValue().getInvoiceId()).isEqualTo(invoiceId);
        assertThat(captor.getValue().isEmailSent()).isTrue();
        assertThat(captor.getValue().getEmailSentAt()).isNotNull();
    }

    @Test
    void shouldTreatQuotedOkAsSuccessfulEmailSend() {
        when(meritApiClient.sendInvoiceByEmail("id", false)).thenReturn("\"OK\"");
        when(invoiceEmailStatusRepository.findById("id")).thenReturn(Optional.empty());
        when(invoiceEmailStatusRepository.save(any(InvoiceEmailStatus.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SendInvoiceEmailResponse response = invoicesService.sendInvoiceByEmail("id", false);

        assertThat(response.status()).isEqualTo("OK");
        verify(invoiceEmailStatusRepository).save(any(InvoiceEmailStatus.class));
    }

    @Test
    void shouldReturnBadGatewayWhenEmailSendFails() {
        when(meritApiClient.sendInvoiceByEmail("id", false)).thenReturn("Mailbox unavailable");

        assertThatThrownBy(() -> invoicesService.sendInvoiceByEmail("id", false))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException statusEx = (ResponseStatusException) ex;
                    assertThat(statusEx.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(statusEx.getReason()).isEqualTo("Mailbox unavailable");
                });
        verify(invoiceEmailStatusRepository, never()).save(any());
    }

    @Test
    void shouldReturnMeritMessageWhenEmailSendIsRejected() {
        String meritBody = "{\"Message\":\"E-mail nadawcy nie został wpisany w ustawieniach faktury sprzedaży.\"}";
        when(meritApiClient.sendInvoiceByEmail("id", false)).thenThrow(
                HttpClientErrorException.create(
                        HttpStatus.BAD_REQUEST,
                        "Bad Request",
                        HttpHeaders.EMPTY,
                        meritBody.getBytes(StandardCharsets.UTF_8),
                        StandardCharsets.UTF_8));

        assertThatThrownBy(() -> invoicesService.sendInvoiceByEmail("id", false))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException statusEx = (ResponseStatusException) ex;
                    assertThat(statusEx.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(statusEx.getReason())
                            .isEqualTo("E-mail nadawcy nie został wpisany w ustawieniach faktury sprzedaży.");
                });
        verify(invoiceEmailStatusRepository, never()).save(any());
    }

    @Test
    void shouldCreateInvoice() {
        CreateInvoiceRequest request = validCreateInvoiceRequest();
        when(meritApiClient.createInvoice(any())).thenReturn(new MeritCreateInvoiceResponse(
                "5f91033c-9d0f-416e-a079-d3c892b8c317",
                "665f01a4-357a-4a6b-a565-2f17e6e1da13"));

        CreateInvoiceResponse response = invoicesService.createInvoice(request);

        assertThat(response.invoiceId()).isEqualTo("5f91033c-9d0f-416e-a079-d3c892b8c317");
        assertThat(response.customerId()).isEqualTo("665f01a4-357a-4a6b-a565-2f17e6e1da13");

        ArgumentCaptor<MeritCreateInvoiceRequest> captor = ArgumentCaptor.forClass(MeritCreateInvoiceRequest.class);
        verify(meritApiClient).createInvoice(captor.capture());
        MeritCreateInvoiceRequest meritRequest = captor.getValue();
        assertThat(meritRequest.customer().id()).isEqualTo("665f01a4-357a-4a6b-a565-2f17e6e1da13");
        assertThat(meritRequest.accountingDoc()).isEqualTo(1);
        assertThat(meritRequest.docDate()).isEqualTo("20260101000000");
        assertThat(meritRequest.dueDate()).isEqualTo("20260115000000");
        assertThat(meritRequest.invoiceNo()).isEqualTo("FV/2026/01/01");
        assertThat(meritRequest.hComment()).isEqualTo("Komentarz górny");
        assertThat(meritRequest.fComment()).isEqualTo("Komentarz dolny");
        assertThat(meritRequest.invoiceRow()).hasSize(1);
        assertThat(meritRequest.taxAmount()).hasSize(1);
    }

    private static CreateInvoiceRequest validCreateInvoiceRequest() {
        return new CreateInvoiceRequest(
                "665f01a4-357a-4a6b-a565-2f17e6e1da13",
                "FV/2026/01/01",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 15),
                "PLN",
                "Komentarz górny",
                "Komentarz dolny",
                new BigDecimal("100.00"),
                List.of(new CreateInvoiceLineRequest(
                        "USLUGA",
                        "Usługa",
                        2,
                        new BigDecimal("1"),
                        new BigDecimal("100.00"),
                        "665f01a4-357a-4a6b-a565-2f17e6e1da13")),
                List.of(new CreateInvoiceTaxAmountRequest(
                        "665f01a4-357a-4a6b-a565-2f17e6e1da13",
                        new BigDecimal("23.00"))));
    }
}
