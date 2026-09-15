package pl.tw.ksiegowosc.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import pl.tw.ksiegowosc.entity.AllegroAccount;

public interface AllegroAccountRepository extends JpaRepository<AllegroAccount, Long> {

    List<AllegroAccount> findByUserIdOrderByNameAsc(Long userId);

    Optional<AllegroAccount> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndClientId(Long userId, String clientId);
}
