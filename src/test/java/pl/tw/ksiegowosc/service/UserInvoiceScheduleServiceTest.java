package pl.tw.ksiegowosc.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import pl.tw.ksiegowosc.entity.UserInvoiceSchedule;

class UserInvoiceScheduleServiceTest {

    @Test
    void isDueWhenNeverRun() {
        UserInvoiceSchedule schedule = schedule(true, 15, null);
        assertThat(UserInvoiceScheduleService.isDue(schedule, Instant.parse("2026-10-05T12:00:00Z"))).isTrue();
    }

    @Test
    void isDueWhenIntervalElapsed() {
        UserInvoiceSchedule schedule = schedule(true, 10, Instant.parse("2026-10-05T11:50:00Z"));
        assertThat(UserInvoiceScheduleService.isDue(schedule, Instant.parse("2026-10-05T12:00:00Z"))).isTrue();
    }

    @Test
    void isNotDueWhenIntervalNotElapsed() {
        UserInvoiceSchedule schedule = schedule(true, 15, Instant.parse("2026-10-05T11:50:00Z"));
        assertThat(UserInvoiceScheduleService.isDue(schedule, Instant.parse("2026-10-05T12:00:00Z"))).isFalse();
    }

    @Test
    void isNotDueWhenDisabled() {
        UserInvoiceSchedule schedule = schedule(false, 1, null);
        assertThat(UserInvoiceScheduleService.isDue(schedule, Instant.parse("2026-10-05T12:00:00Z"))).isFalse();
    }

    @Test
    void isNotDueWhenFromDateMissing() {
        UserInvoiceSchedule schedule = schedule(true, 1, null);
        schedule.setInvoicesFromDate(null);
        assertThat(UserInvoiceScheduleService.isDue(schedule, Instant.parse("2026-10-05T12:00:00Z"))).isFalse();
    }

    private static UserInvoiceSchedule schedule(boolean enabled, int interval, Instant lastRun) {
        UserInvoiceSchedule schedule = new UserInvoiceSchedule();
        schedule.setUserId(1L);
        schedule.setEnabled(enabled);
        schedule.setIntervalMinutes(interval);
        schedule.setInvoicesFromDate(LocalDate.of(2026, 1, 1));
        schedule.setLiveMode(false);
        schedule.setLastRunAt(lastRun);
        return schedule;
    }
}
