package pl.tw.ksiegowosc.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "vaadin.launch-browser=false")
class SecretEncryptionMigratorTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SecretCryptoService cryptoService;

    @Autowired
    private SecretEncryptionMigrator migrator;

    @Test
    @Transactional
    void shouldEncryptLegacyPlaintextAndStayIdempotent() {
        Instant now = Instant.parse("2026-01-15T10:00:00Z");
        jdbcTemplate.update(
                """
                INSERT INTO app_user (id, login, password_hash)
                VALUES (9001, 'enc-test', 'x')
                """);
        jdbcTemplate.update(
                """
                INSERT INTO user_api_credentials (user_id, merit_api_id, merit_api_key, updated_at)
                VALUES (9001, 'api-id', 'plain-merit-key', ?)
                """,
                now);
        jdbcTemplate.update(
                """
                INSERT INTO allegro_account (id, user_id, name, client_id, client_secret, invoice_prefix, created_at, updated_at)
                VALUES (9001, 9001, 'Shop', 'cid', 'plain-client-secret', 'FS', ?, ?)
                """,
                now,
                now);
        jdbcTemplate.update(
                """
                INSERT INTO allegro_token (account_id, access_token, refresh_token, expires_at, updated_at)
                VALUES (9001, 'plain-access', 'plain-refresh', ?, ?)
                """,
                now.plusSeconds(3600),
                now);

        migrator.run(null);

        String meritKey = jdbcTemplate.queryForObject(
                "SELECT merit_api_key FROM user_api_credentials WHERE user_id = 9001", String.class);
        String clientSecret = jdbcTemplate.queryForObject(
                "SELECT client_secret FROM allegro_account WHERE id = 9001", String.class);
        String accessToken = jdbcTemplate.queryForObject(
                "SELECT access_token FROM allegro_token WHERE account_id = 9001", String.class);
        String refreshToken = jdbcTemplate.queryForObject(
                "SELECT refresh_token FROM allegro_token WHERE account_id = 9001", String.class);

        assertThat(meritKey).startsWith(SecretCryptoService.PREFIX);
        assertThat(clientSecret).startsWith(SecretCryptoService.PREFIX);
        assertThat(accessToken).startsWith(SecretCryptoService.PREFIX);
        assertThat(refreshToken).startsWith(SecretCryptoService.PREFIX);
        assertThat(cryptoService.decrypt(meritKey)).isEqualTo("plain-merit-key");
        assertThat(cryptoService.decrypt(clientSecret)).isEqualTo("plain-client-secret");
        assertThat(cryptoService.decrypt(accessToken)).isEqualTo("plain-access");
        assertThat(cryptoService.decrypt(refreshToken)).isEqualTo("plain-refresh");

        String meritKeyAfterFirst = meritKey;
        migrator.run(null);
        String meritKeyAfterSecond = jdbcTemplate.queryForObject(
                "SELECT merit_api_key FROM user_api_credentials WHERE user_id = 9001", String.class);
        assertThat(meritKeyAfterSecond).isEqualTo(meritKeyAfterFirst);
    }
}
