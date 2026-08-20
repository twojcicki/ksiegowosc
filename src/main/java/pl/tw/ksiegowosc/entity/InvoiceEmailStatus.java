package pl.tw.ksiegowosc.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "invoice_email_status")
public class InvoiceEmailStatus {

    @Id
    @Column(name = "invoice_id", length = 64, nullable = false)
    private String invoiceId;

    @Column(name = "email_sent", nullable = false)
    private boolean emailSent;

    @Column(name = "email_sent_at")
    private Instant emailSentAt;

    public String getInvoiceId() {
        return invoiceId;
    }

    public void setInvoiceId(String invoiceId) {
        this.invoiceId = invoiceId;
    }

    public boolean isEmailSent() {
        return emailSent;
    }

    public void setEmailSent(boolean emailSent) {
        this.emailSent = emailSent;
    }

    public Instant getEmailSentAt() {
        return emailSentAt;
    }

    public void setEmailSentAt(Instant emailSentAt) {
        this.emailSentAt = emailSentAt;
    }
}
