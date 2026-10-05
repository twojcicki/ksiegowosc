package pl.tw.ksiegowosc.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import pl.tw.ksiegowosc.dto.AllegroSoldItemDto;
import pl.tw.ksiegowosc.dto.AllegroSoldItemsResult;
import pl.tw.ksiegowosc.entity.AppUser;
import pl.tw.ksiegowosc.entity.UserInvoiceSchedule;
import pl.tw.ksiegowosc.repository.AllegroTrialInvoiceRepository;
import pl.tw.ksiegowosc.security.RunAsUser;

@Service
public class AllegroInvoiceAutoIssueService {

    private static final Logger log = LoggerFactory.getLogger(AllegroInvoiceAutoIssueService.class);

    private final UserInvoiceScheduleService scheduleService;
    private final CurrentUserApiCredentialsService credentialsService;
    private final AllegroOrdersService ordersService;
    private final AllegroInvoiceService invoiceService;
    private final AllegroTrialInvoiceRepository trialInvoiceRepository;
    private final Clock clock;

    public AllegroInvoiceAutoIssueService(
            UserInvoiceScheduleService scheduleService,
            CurrentUserApiCredentialsService credentialsService,
            AllegroOrdersService ordersService,
            AllegroInvoiceService invoiceService,
            AllegroTrialInvoiceRepository trialInvoiceRepository,
            Clock clock) {
        this.scheduleService = scheduleService;
        this.credentialsService = credentialsService;
        this.ordersService = ordersService;
        this.invoiceService = invoiceService;
        this.trialInvoiceRepository = trialInvoiceRepository;
        this.clock = clock;
    }

    public void runDueSchedules() {
        Instant now = Instant.now(clock);
        List<UserInvoiceSchedule> due = scheduleService.findDueSchedules(now);
        for (UserInvoiceSchedule schedule : due) {
            try {
                runForSchedule(schedule, now);
            } catch (RuntimeException ex) {
                log.warn(
                        "Auto-wystawianie faktur nie powiodło się dla userId={}: {}",
                        schedule.getUserId(),
                        ex.toString());
            }
        }
    }

    void runForSchedule(UserInvoiceSchedule schedule, Instant now) {
        AppUser user = credentialsService.requireUserById(schedule.getUserId());
        RunAsUser.run(user.getLogin(), () -> processUser(schedule));
        scheduleService.markRun(schedule.getUserId(), now);
    }

    private void processUser(UserInvoiceSchedule schedule) {
        AllegroSoldItemsResult sold = ordersService.getSoldItemsFrom(schedule.getInvoicesFromDate());
        List<AllegroSoldItemDto> withoutLive = filterCandidates(sold.items());
        List<AllegroSoldItemDto> candidates = filterWithoutTrial(withoutLive);
        log.info(
                "Auto-wystawianie userId={} live={} kandydatów={} ostrzeżeń={}",
                schedule.getUserId(),
                schedule.isLiveMode(),
                candidates.size(),
                sold.warnings().size());

        for (AllegroSoldItemDto item : candidates) {
            try {
                if (schedule.isLiveMode()) {
                    invoiceService.issueInvoice(item.accountId(), item.orderId());
                } else {
                    invoiceService.issueTrialInvoice(schedule.getUserId(), item.accountId(), item.orderId());
                }
            } catch (RuntimeException ex) {
                String message = resolveErrorMessage(ex);
                log.warn(
                        "Auto-wystawianie nie powiodło się orderId={} accountId={} live={}: {}",
                        item.orderId(),
                        item.accountId(),
                        schedule.isLiveMode(),
                        message);
                if (schedule.isLiveMode()) {
                    try {
                        invoiceService.recordIssueError(item.orderId(), message);
                    } catch (RuntimeException persistEx) {
                        log.warn("Nie udało się zapisać issue_error dla orderId={}", item.orderId(), persistEx);
                    }
                }
            }
        }
    }

    static List<AllegroSoldItemDto> filterCandidates(List<AllegroSoldItemDto> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<AllegroSoldItemDto> result = new ArrayList<>();
        for (AllegroSoldItemDto item : items) {
            if (item == null || item.orderId() == null || item.orderId().isBlank()) {
                continue;
            }
            if (item.invoiceNo() != null && !item.invoiceNo().isBlank()) {
                continue;
            }
            result.add(item);
        }
        return result;
    }

    List<AllegroSoldItemDto> filterWithoutTrial(List<AllegroSoldItemDto> candidates) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        List<String> orderIds = candidates.stream()
                .map(AllegroSoldItemDto::orderId)
                .filter(Objects::nonNull)
                .toList();
        Set<String> trialOrderIds = new HashSet<>(trialInvoiceRepository.findOrderIdsByOrderIdIn(orderIds));
        if (trialOrderIds.isEmpty()) {
            return candidates;
        }
        return candidates.stream()
                .filter(item -> !trialOrderIds.contains(item.orderId()))
                .toList();
    }

    private static String resolveErrorMessage(RuntimeException ex) {
        if (ex instanceof ResponseStatusException statusEx
                && statusEx.getReason() != null
                && !statusEx.getReason().isBlank()) {
            return statusEx.getReason();
        }
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return "Nie udało się wystawić faktury.";
        }
        return message;
    }
}
