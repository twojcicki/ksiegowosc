package pl.tw.ksiegowosc.dto;

import java.time.LocalDate;

public record UserInvoiceScheduleDto(
        boolean enabled,
        int intervalMinutes,
        LocalDate invoicesFromDate,
        boolean liveMode
) {
}
