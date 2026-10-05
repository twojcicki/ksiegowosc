package pl.tw.ksiegowosc.entity;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_invoice_schedule")
public class UserInvoiceSchedule {

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "interval_minutes", nullable = false)
    private int intervalMinutes;

    @Column(name = "invoices_from_date")
    private LocalDate invoicesFromDate;

    @Column(name = "live_mode", nullable = false)
    private boolean liveMode;

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getIntervalMinutes() {
        return intervalMinutes;
    }

    public void setIntervalMinutes(int intervalMinutes) {
        this.intervalMinutes = intervalMinutes;
    }

    public LocalDate getInvoicesFromDate() {
        return invoicesFromDate;
    }

    public void setInvoicesFromDate(LocalDate invoicesFromDate) {
        this.invoicesFromDate = invoicesFromDate;
    }

    public boolean isLiveMode() {
        return liveMode;
    }

    public void setLiveMode(boolean liveMode) {
        this.liveMode = liveMode;
    }

    public Instant getLastRunAt() {
        return lastRunAt;
    }

    public void setLastRunAt(Instant lastRunAt) {
        this.lastRunAt = lastRunAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
