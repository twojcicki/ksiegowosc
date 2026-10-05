package pl.tw.ksiegowosc.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import pl.tw.ksiegowosc.service.AllegroInvoiceAutoIssueService;

@Component
public class AllegroInvoiceAutoIssueJob {

    private static final Logger log = LoggerFactory.getLogger(AllegroInvoiceAutoIssueJob.class);

    private final AllegroInvoiceAutoIssueService autoIssueService;

    public AllegroInvoiceAutoIssueJob(AllegroInvoiceAutoIssueService autoIssueService) {
        this.autoIssueService = autoIssueService;
    }

    @Scheduled(fixedDelayString = "60000")
    public void tick() {
        try {
            autoIssueService.runDueSchedules();
        } catch (RuntimeException ex) {
            log.warn("Tick auto-wystawiania faktur nie powiódł się: {}", ex.toString());
        }
    }
}
