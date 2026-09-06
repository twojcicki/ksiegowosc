package pl.tw.ksiegowosc.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "allegro_sold_invoice")
public class AllegroSoldInvoice {

    @Id
    @Column(name = "order_id", length = 64, nullable = false)
    private String orderId;

    @Column(name = "invoice_no", length = 35, nullable = false, unique = true)
    private String invoiceNo;

    @Column(name = "merit_invoice_id", length = 64)
    private String meritInvoiceId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getInvoiceNo() {
        return invoiceNo;
    }

    public void setInvoiceNo(String invoiceNo) {
        this.invoiceNo = invoiceNo;
    }

    public String getMeritInvoiceId() {
        return meritInvoiceId;
    }

    public void setMeritInvoiceId(String meritInvoiceId) {
        this.meritInvoiceId = meritInvoiceId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
