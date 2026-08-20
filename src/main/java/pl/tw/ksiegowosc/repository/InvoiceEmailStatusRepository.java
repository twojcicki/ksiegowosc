package pl.tw.ksiegowosc.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import pl.tw.ksiegowosc.entity.InvoiceEmailStatus;

public interface InvoiceEmailStatusRepository extends JpaRepository<InvoiceEmailStatus, String> {

    List<InvoiceEmailStatus> findAllByInvoiceIdIn(Collection<String> invoiceIds);
}
