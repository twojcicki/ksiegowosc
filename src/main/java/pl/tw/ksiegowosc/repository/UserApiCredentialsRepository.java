package pl.tw.ksiegowosc.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import pl.tw.ksiegowosc.entity.UserApiCredentials;

public interface UserApiCredentialsRepository extends JpaRepository<UserApiCredentials, Long> {
}
