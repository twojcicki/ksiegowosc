package pl.tw.ksiegowosc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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
    private InvoicesService invoicesService;
    private CurrentUserApiCredentialsService credentialsService;
    private AllegroAccountService service;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AllegroAccountRepository.class);
        tokenRepository = mock(AllegroTokenRepository.class);
        invoicesService = mock(InvoicesService.class);
        credentialsService = mock(CurrentUserApiCredentialsService.class);
        when(credentialsService.requireCurrentUserId()).thenReturn(3L);
        service = new AllegroAccountService(
                accountRepository,
                tokenRepository,
                invoicesService,
                credentialsService,
                Clock.fixed(Instant.parse("2026-09-15T10:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void shouldAddAccountWithInvoicePrefix() {
        when(accountRepository.existsByUserIdAndClientId(3L, "cid")).thenReturn(false);
        when(accountRepository.save(any(AllegroAccount.class))).thenAnswer(inv -> {
            AllegroAccount account = inv.getArgument(0);
            account.setId(11L);
            return account;
        });

        var dto = service.addAccount(
                "Sklep",
                "cid",
                "secret",
                "FS",
                AllegroAccountService.DEFAULT_API_BASE_URL,
                AllegroAccountService.DEFAULT_AUTH_URL,
                AllegroAccountService.DEFAULT_USER_AGENT);

        assertThat(dto.id()).isEqualTo(11L);
        assertThat(dto.name()).isEqualTo("Sklep");
        assertThat(dto.clientId()).isEqualTo("cid");
        assertThat(dto.invoicePrefix()).isEqualTo("FS");
        assertThat(dto.apiBaseUrl()).isEqualTo(AllegroAccountService.DEFAULT_API_BASE_URL);
        assertThat(dto.authUrl()).isEqualTo(AllegroAccountService.DEFAULT_AUTH_URL);
        assertThat(dto.userAgent()).isEqualTo(AllegroAccountService.DEFAULT_USER_AGENT);
        assertThat(dto.connected()).isFalse();
        verify(accountRepository).save(any(AllegroAccount.class));
    }

    @Test
    void shouldRejectDuplicateClientId() {
        when(accountRepository.existsByUserIdAndClientId(3L, "cid")).thenReturn(true);

        assertThatThrownBy(() -> service.addAccount(
                        "Sklep",
                        "cid",
                        "secret",
                        "FS",
                        AllegroAccountService.DEFAULT_API_BASE_URL,
                        AllegroAccountService.DEFAULT_AUTH_URL,
                        AllegroAccountService.DEFAULT_USER_AGENT))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("już istnieje");
    }

    @Test
    void shouldRejectBlankInvoicePrefix() {
        assertThatThrownBy(() -> service.addAccount(
                        "Sklep",
                        "cid",
                        "secret",
                        "  ",
                        AllegroAccountService.DEFAULT_API_BASE_URL,
                        AllegroAccountService.DEFAULT_AUTH_URL,
                        AllegroAccountService.DEFAULT_USER_AGENT))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("prefiks");
    }

    @Test
    void shouldListAccountsWithConnectionFlag() {
        AllegroAccount account = ownedAccount();
        when(accountRepository.findByUserIdOrderByNameAsc(3L)).thenReturn(List.of(account));
        when(tokenRepository.findAllById(List.of(11L))).thenReturn(List.of());

        assertThat(service.listAccounts()).hasSize(1);
        assertThat(service.listAccounts().getFirst().connected()).isFalse();
        assertThat(service.listAccounts().getFirst().invoicePrefix()).isEqualTo("FS");
    }

    @Test
    void shouldUpdateAccountFields() {
        AllegroAccount account = ownedAccount();
        when(accountRepository.findByIdAndUserId(11L, 3L)).thenReturn(Optional.of(account));
        when(tokenRepository.existsById(11L)).thenReturn(true);

        var dto = service.updateAccount(
                11L,
                "Sklep 2",
                "FV",
                "https://api.allegro.pl.allegrosandbox.pl",
                "https://allegro.pl.allegrosandbox.pl",
                "App/1.0 (+https://example.test)");

        assertThat(dto.name()).isEqualTo("Sklep 2");
        assertThat(dto.invoicePrefix()).isEqualTo("FV");
        assertThat(dto.apiBaseUrl()).isEqualTo("https://api.allegro.pl.allegrosandbox.pl");
        assertThat(dto.authUrl()).isEqualTo("https://allegro.pl.allegrosandbox.pl");
        assertThat(dto.userAgent()).isEqualTo("App/1.0 (+https://example.test)");
        assertThat(dto.clientId()).isEqualTo("cid");
        assertThat(dto.connected()).isTrue();
        assertThat(account.getName()).isEqualTo("Sklep 2");
        assertThat(account.getUpdatedAt()).isEqualTo(Instant.parse("2026-09-15T10:00:00Z"));
    }

    @Test
    void shouldRejectBlankNameOnUpdate() {
        when(accountRepository.findByIdAndUserId(11L, 3L)).thenReturn(Optional.of(ownedAccount()));

        assertThatThrownBy(() -> service.updateAccount(
                        11L,
                        "  ",
                        "FS",
                        AllegroAccountService.DEFAULT_API_BASE_URL,
                        AllegroAccountService.DEFAULT_AUTH_URL,
                        AllegroAccountService.DEFAULT_USER_AGENT))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("nazwę");
    }

    @Test
    void shouldRejectNonHttpsUrlOnUpdate() {
        when(accountRepository.findByIdAndUserId(11L, 3L)).thenReturn(Optional.of(ownedAccount()));

        assertThatThrownBy(() -> service.updateAccount(
                        11L,
                        "Sklep",
                        "FS",
                        "http://api.allegro.pl",
                        AllegroAccountService.DEFAULT_AUTH_URL,
                        AllegroAccountService.DEFAULT_USER_AGENT))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("https://");
    }

    @Test
    void shouldDeleteOwnedAccount() {
        AllegroAccount account = ownedAccount();
        when(accountRepository.findByIdAndUserId(11L, 3L)).thenReturn(Optional.of(account));

        service.deleteAccount(11L);

        verify(accountRepository).delete(account);
    }

    @Test
    void shouldAllocateInvoiceNoFromMerit() {
        when(accountRepository.findByIdAndUserId(11L, 3L)).thenReturn(Optional.of(ownedAccount()));
        when(invoicesService.nextInvoiceNoFromMerit("FS", LocalDate.of(2026, 9, 6)))
                .thenReturn("FS/5/09/2026");

        String invoiceNo = service.allocateInvoiceNo(11L, LocalDate.of(2026, 9, 6));

        assertThat(invoiceNo).isEqualTo("FS/5/09/2026");
        verify(invoicesService).nextInvoiceNoFromMerit("FS", LocalDate.of(2026, 9, 6));
    }

    private static AllegroAccount ownedAccount() {
        AllegroAccount account = new AllegroAccount();
        account.setId(11L);
        account.setUserId(3L);
        account.setName("Sklep");
        account.setClientId("cid");
        account.setClientSecret("secret");
        account.setInvoicePrefix("FS");
        account.setApiBaseUrl(AllegroAccountService.DEFAULT_API_BASE_URL);
        account.setAuthUrl(AllegroAccountService.DEFAULT_AUTH_URL);
        account.setUserAgent(AllegroAccountService.DEFAULT_USER_AGENT);
        return account;
    }
}
