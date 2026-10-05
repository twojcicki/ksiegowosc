package pl.tw.ksiegowosc.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import pl.tw.ksiegowosc.dto.UserInvoiceScheduleDto;
import pl.tw.ksiegowosc.entity.UserInvoiceSchedule;
import pl.tw.ksiegowosc.repository.UserInvoiceScheduleRepository;

@Service
public class UserInvoiceScheduleService {

    public static final int DEFAULT_INTERVAL_MINUTES = 15;

    private final UserInvoiceScheduleRepository scheduleRepository;
    private final CurrentUserApiCredentialsService credentialsService;
    private final Clock clock;

    public UserInvoiceScheduleService(
            UserInvoiceScheduleRepository scheduleRepository,
            CurrentUserApiCredentialsService credentialsService,
            Clock clock) {
        this.scheduleRepository = scheduleRepository;
        this.credentialsService = credentialsService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public UserInvoiceScheduleDto getSettings() {
        return scheduleRepository.findById(credentialsService.requireCurrentUserId())
                .map(this::toDto)
                .orElseGet(this::defaults);
    }

    @Transactional
    public UserInvoiceScheduleDto saveSettings(
            boolean enabled,
            int intervalMinutes,
            LocalDate invoicesFromDate,
            boolean liveMode) {
        if (intervalMinutes < 1 || intervalMinutes > 60) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Interwał musi być w zakresie 1–60 minut.");
        }
        if (enabled && invoicesFromDate == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Przy włączonym harmonogramie podaj datę „Wystawiaj od”.");
        }

        Long userId = credentialsService.requireCurrentUserId();
        UserInvoiceSchedule schedule = scheduleRepository.findById(userId).orElseGet(() -> {
            UserInvoiceSchedule created = new UserInvoiceSchedule();
            created.setUserId(userId);
            created.setLastRunAt(null);
            return created;
        });
        schedule.setEnabled(enabled);
        schedule.setIntervalMinutes(intervalMinutes);
        schedule.setInvoicesFromDate(invoicesFromDate);
        schedule.setLiveMode(liveMode);
        schedule.setUpdatedAt(Instant.now(clock));
        scheduleRepository.save(schedule);
        return toDto(schedule);
    }

    @Transactional(readOnly = true)
    public List<UserInvoiceSchedule> findDueSchedules(Instant now) {
        List<UserInvoiceSchedule> due = new ArrayList<>();
        for (UserInvoiceSchedule schedule : scheduleRepository.findByEnabledTrue()) {
            if (isDue(schedule, now)) {
                due.add(schedule);
            }
        }
        return due;
    }

    static boolean isDue(UserInvoiceSchedule schedule, Instant now) {
        if (schedule == null || !schedule.isEnabled()) {
            return false;
        }
        if (schedule.getInvoicesFromDate() == null) {
            return false;
        }
        Instant lastRun = schedule.getLastRunAt();
        if (lastRun == null) {
            return true;
        }
        Instant next = lastRun.plus(Duration.ofMinutes(schedule.getIntervalMinutes()));
        return !next.isAfter(now);
    }

    @Transactional
    public void markRun(Long userId, Instant runAt) {
        UserInvoiceSchedule schedule = scheduleRepository.findById(userId).orElse(null);
        if (schedule == null) {
            return;
        }
        schedule.setLastRunAt(runAt);
        schedule.setUpdatedAt(Instant.now(clock));
        scheduleRepository.save(schedule);
    }

    private UserInvoiceScheduleDto toDto(UserInvoiceSchedule schedule) {
        return new UserInvoiceScheduleDto(
                schedule.isEnabled(),
                schedule.getIntervalMinutes(),
                schedule.getInvoicesFromDate(),
                schedule.isLiveMode());
    }

    private UserInvoiceScheduleDto defaults() {
        return new UserInvoiceScheduleDto(false, DEFAULT_INTERVAL_MINUTES, null, false);
    }
}
