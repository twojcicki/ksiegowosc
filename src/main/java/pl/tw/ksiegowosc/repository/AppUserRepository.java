package pl.tw.ksiegowosc.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import pl.tw.ksiegowosc.entity.AppUser;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByLogin(String login);
}
