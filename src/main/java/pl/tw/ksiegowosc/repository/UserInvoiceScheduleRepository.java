package pl.tw.ksiegowosc.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import pl.tw.ksiegowosc.entity.UserInvoiceSchedule;

public interface UserInvoiceScheduleRepository extends JpaRepository<UserInvoiceSchedule, Long> {

    List<UserInvoiceSchedule> findByEnabledTrue();
}
