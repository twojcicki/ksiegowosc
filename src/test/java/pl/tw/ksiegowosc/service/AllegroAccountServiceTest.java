package pl.tw.ksiegowosc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import pl.tw.ksiegowosc.entity.AllegroAccount;
import pl.tw.ksiegowosc.repository.AllegroAccountRepository;
import pl.tw.ksiegowosc.repository.AllegroTokenRepository;

class AllegroAccountServiceTest {

    private AllegroAccountRepository accountRepository;
    private AllegroTokenRepository tokenRepository;
    private CurrentUserApiCredentialsService credentialsService;
    private AllegroAccountService service;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AllegroAccountRepository.class);
        tokenRepository = mock(AllegroTokenRepository.class);
        credentialsService = mock(CurrentUserApiCredentialsService.class);
        when(credentialsService.requireCurrentUserId()).thenReturn(3L);
        service = new AllegroAccountService(
                accountRepository,
                tokenRepository,
                credentialsService,
                Clock.fixed(Instant.parse("2026-09-15T10:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void shouldAddAccount() {
        when(accountRepository.existsByUserIdAndClientId(3L, "cid")).thenReturn(false);
        when(accountRepository.save(any(AllegroAccount.class))).thenAnswer(inv -> {
            AllegroAccount account = inv.getArgument(0);
            account.setId(11L);
            return account;
        });

        var dto = service.addAccount("Sklep", "cid", "secret");

        assertThat(dto.id()).isEqualTo(11L);
        assertThat(dto.name()).isEqualTo("Sklep");
        assertThat(dto.clientId()).isEqualTo("cid");
        assertThat(dto.connected()).isFalse();
        verify(accountRepository).save(any(AllegroAccount.class));
    }

    @Test
    void shouldRejectDuplicateClientId() {
        when(accountRepository.existsByUserIdAndClientId(3L, "cid")).thenReturn(true);

        assertThatThrownBy(() -> service.addAccount("Sklep", "cid", "secret"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("już istnieje");
    }

    @Test
    void shouldListAccountsWithConnectionFlag() {
        AllegroAccount account = new AllegroAccount();
        account.setId(11L);
        account.setUserId(3L);
        account.setName("Sklep");
        account.setClientId("cid");
        account.setClientSecret("secret");
        when(accountRepository.findByUserIdOrderByNameAsc(3L)).thenReturn(List.of(account));
        when(tokenRepository.findAllById(List.of(11L))).thenReturn(List.of());

        assertThat(service.listAccounts()).hasSize(1);
        assertThat(service.listAccounts().getFirst().connected()).isFalse();
    }

    @Test
    void shouldDeleteOwnedAccount() {
        AllegroAccount account = new AllegroAccount();
        account.setId(11L);
        account.setUserId(3L);
        when(accountRepository.findByIdAndUserId(11L, 3L)).thenReturn(Optional.of(account));

        service.deleteAccount(11L);

        verify(accountRepository).delete(account);
    }
}
