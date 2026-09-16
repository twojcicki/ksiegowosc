package pl.tw.ksiegowosc.security;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Component
public class SecretEncryptionMigrator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SecretEncryptionMigrator.class);

    private final JdbcTemplate jdbcTemplate;
    private final SecretCryptoService cryptoService;

    public SecretEncryptionMigrator(JdbcTemplate jdbcTemplate, SecretCryptoService cryptoService) {
        this.jdbcTemplate = jdbcTemplate;
        this.cryptoService = cryptoService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int updated = 0;
        updated += encryptColumn("user_api_credentials", "user_id", "merit_api_key");
        updated += encryptColumn("allegro_account", "id", "client_secret");
        updated += encryptColumn("allegro_token", "account_id", "access_token");
        updated += encryptColumn("allegro_token", "account_id", "refresh_token");
        if (updated > 0) {
            log.info("Encrypted {} legacy plaintext secret value(s) at rest", updated);
        } else {
            log.debug("No legacy plaintext secrets to encrypt");
        }
    }

    private int encryptColumn(String table, String idColumn, String secretColumn) {
        String selectSql = """
                SELECT %s AS id, %s AS secret
                FROM %s
                WHERE %s IS NOT NULL
                  AND TRIM(%s) <> ''
                  AND %s NOT LIKE ?
                """.formatted(idColumn, secretColumn, table, secretColumn, secretColumn, secretColumn);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(selectSql, SecretCryptoService.PREFIX + "%");
        int count = 0;
        String updateSql = "UPDATE %s SET %s = ? WHERE %s = ?".formatted(table, secretColumn, idColumn);
        for (Map<String, Object> row : rows) {
            Object id = row.get("id");
            String secret = (String) row.get("secret");
            if (!StringUtils.hasText(secret) || cryptoService.looksEncrypted(secret)) {
                continue;
            }
            jdbcTemplate.update(updateSql, cryptoService.encrypt(secret), id);
            count++;
        }
        return count;
    }
}
