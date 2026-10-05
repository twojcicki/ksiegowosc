package pl.tw.ksiegowosc.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pl.tw.ksiegowosc.entity.AllegroTrialInvoice;

public interface AllegroTrialInvoiceRepository extends JpaRepository<AllegroTrialInvoice, Long> {

    List<AllegroTrialInvoice> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<AllegroTrialInvoice> findByIdAndUserId(Long id, Long userId);

    boolean existsByOrderId(String orderId);

    @Query("select t.orderId from AllegroTrialInvoice t where t.orderId in :orderIds")
    Set<String> findOrderIdsByOrderIdIn(@Param("orderIds") Collection<String> orderIds);
}
