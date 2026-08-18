package pl.tw.ksiegowosc.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Service;

import pl.tw.ksiegowosc.client.MeritApiClient;
import pl.tw.ksiegowosc.dto.SalesInvoiceDto;

@Service
public class InvoicesService {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    private final MeritApiClient meritApiClient;
    private final Clock clock;

    public InvoicesService(MeritApiClient meritApiClient, Clock clock) {
        this.meritApiClient = meritApiClient;
        this.clock = clock;
    }

    public List<SalesInvoiceDto> getYesterdaysInvoices() {
        LocalDate yesterday = LocalDate.now(clock.withZone(WARSAW)).minusDays(1);
        return meritApiClient.getInvoices(yesterday);
    }
}
