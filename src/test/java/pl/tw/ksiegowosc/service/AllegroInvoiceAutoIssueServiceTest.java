package pl.tw.ksiegowosc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pl.tw.ksiegowosc.dto.AllegroSoldItemDto;
import pl.tw.ksiegowosc.dto.AllegroSoldItemsResult;
import pl.tw.ksiegowosc.entity.AppUser;
import pl.tw.ksiegowosc.entity.UserInvoiceSchedule;
import pl.tw.ksiegowosc.repository.AllegroTrialInvoiceRepository;

class AllegroInvoiceAutoIssueServiceTest {

    private UserInvoiceScheduleService scheduleService;
    private CurrentUserApiCredentialsService credentialsService;
    private AllegroOrdersService ordersService;
    private AllegroInvoiceService invoiceService;
    private AllegroTrialInvoiceRepository trialInvoiceRepository;
    private AllegroInvoiceAutoIssueService autoIssueService;

    @BeforeEach
    void setUp() {
        scheduleService = mock(UserInvoiceScheduleService.class);
        credentialsService = mock(CurrentUserApiCredentialsService.class);
        ordersService = mock(AllegroOrdersService.class);
        invoiceService = mock(AllegroInvoiceService.class);
        trialInvoiceRepository = mock(AllegroTrialInvoiceRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"), ZoneOffset.UTC);
        autoIssueService = new AllegroInvoiceAutoIssueService(
                scheduleService,
                credentialsService,
                ordersService,
                invoiceService,
                trialInvoiceRepository,
                clock);
    }

    @Test
    void filterCandidatesSkipsIssuedInvoices() {
        List<AllegroSoldItemDto> filtered = AllegroInvoiceAutoIssueService.filterCandidates(List.of(
                item("a", null),
                item("b", "FS/1"),
                item("c", "")));
        assertThat(filtered).extracting(AllegroSoldItemDto::orderId).containsExactly("a", "c");
    }

    @Test
    void filterWithoutTrialSkipsExistingTrialOrders() {
        when(trialInvoiceRepository.findOrderIdsByOrderIdIn(any())).thenReturn(Set.of("b"));
        List<AllegroSoldItemDto> filtered = autoIssueService.filterWithoutTrial(List.of(
                item("a", null),
                item("b", null)));
        assertThat(filtered).extracting(AllegroSoldItemDto::orderId).containsExactly("a");
    }

    @Test
    void runForScheduleIssuesTrialWithoutLive() {
        UserInvoiceSchedule schedule = schedule(false);
        AppUser user = new AppUser();
        user.setId(7L);
        user.setLogin("admin");
        when(credentialsService.requireUserById(7L)).thenReturn(user);
        when(ordersService.getSoldItemsFrom(LocalDate.of(2026, 1, 1)))
                .thenReturn(new AllegroSoldItemsResult(List.of(item("ord-1", null)), List.of()));
        when(trialInvoiceRepository.findOrderIdsByOrderIdIn(any())).thenReturn(Set.of());

        autoIssueService.runForSchedule(schedule, Instant.parse("2026-10-05T12:00:00Z"));

        verify(invoiceService).issueTrialInvoice(7L, 9L, "ord-1");
        verify(invoiceService, never()).issueInvoice(any(), any());
        verify(scheduleService).markRun(7L, Instant.parse("2026-10-05T12:00:00Z"));
    }

    @Test
    void runForScheduleIssuesLive() {
        UserInvoiceSchedule schedule = schedule(true);
        AppUser user = new AppUser();
        user.setId(7L);
        user.setLogin("admin");
        when(credentialsService.requireUserById(7L)).thenReturn(user);
        when(ordersService.getSoldItemsFrom(LocalDate.of(2026, 1, 1)))
                .thenReturn(new AllegroSoldItemsResult(List.of(item("ord-2", null)), List.of()));
        when(trialInvoiceRepository.findOrderIdsByOrderIdIn(any())).thenReturn(Set.of());

        autoIssueService.runForSchedule(schedule, Instant.parse("2026-10-05T12:00:00Z"));

        verify(invoiceService).issueInvoice(9L, "ord-2");
        verify(invoiceService, never()).issueTrialInvoice(any(), any(), any());
    }

    private static UserInvoiceSchedule schedule(boolean liveMode) {
        UserInvoiceSchedule schedule = new UserInvoiceSchedule();
        schedule.setUserId(7L);
        schedule.setEnabled(true);
        schedule.setIntervalMinutes(15);
        schedule.setInvoicesFromDate(LocalDate.of(2026, 1, 1));
        schedule.setLiveMode(liveMode);
        return schedule;
    }

    private static AllegroSoldItemDto item(String orderId, String invoiceNo) {
        return new AllegroSoldItemDto(
                9L,
                "Konto",
                orderId,
                "Produkt",
                1,
                1,
                List.of(),
                BigDecimal.TEN,
                "PLN",
                Instant.parse("2026-09-01T10:00:00Z"),
                "buyer",
                "BOUGHT",
                "NEW",
                invoiceNo,
                null);
    }
}
