package pl.tw.ksiegowosc.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import pl.tw.ksiegowosc.entity.AllegroToken;

public interface AllegroTokenRepository extends JpaRepository<AllegroToken, String> {
}
