package pl.tw.ksiegowosc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import pl.tw.ksiegowosc.client.MeritApiClient;
import pl.tw.ksiegowosc.dto.SalesInvoiceDetailsDto;
import pl.tw.ksiegowosc.dto.SalesInvoiceDto;
import pl.tw.ksiegowosc.dto.SalesInvoiceHeaderDto;
import pl.tw.ksiegowosc.dto.SendInvoiceEmailResponse;

class InvoicesServiceTest {

    private MeritApiClient meritApiClient;
    private InvoicesService invoicesService;

    @BeforeEach
    void setUp() {
        meritApiClient = mock(MeritApiClient.class);
        invoicesService = new InvoicesService(meritApiClient);
    }

    @Test
    void shouldFetchInvoicesForValidPeriod() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 31);
        List<SalesInvoiceDto> expected = List.of(new SalesInvoiceDto(
                "id",
                "FV/1",
                "2026-01-15T00:00:00",
                "Klient",
                new BigDecimal("10.00"),
                false));
        when(meritApiClient.getInvoices(from, to)).thenReturn(expected);

        List<SalesInvoiceDto> invoices = invoicesService.getInvoices(from, to);

        assertThat(invoices).isEqualTo(expected);
        verify(meritApiClient).getInvoices(from, to);
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

        SendInvoiceEmailResponse response = invoicesService.sendInvoiceByEmail(invoiceId, false);

        assertThat(response.status()).isEqualTo("OK");
        verify(meritApiClient).sendInvoiceByEmail(invoiceId, false);
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
    }
}
