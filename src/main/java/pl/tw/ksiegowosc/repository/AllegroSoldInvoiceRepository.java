package pl.tw.ksiegowosc.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import pl.tw.ksiegowosc.entity.AllegroSoldInvoice;

public interface AllegroSoldInvoiceRepository extends JpaRepository<AllegroSoldInvoice, String> {

    List<AllegroSoldInvoice> findByOrderIdIn(Collection<String> orderIds);
}
