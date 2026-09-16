package pl.tw.ksiegowosc.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pl.tw.ksiegowosc.config.EncryptionProperties;

class SecretCryptoServiceTest {

    private static final String TEST_KEY_BASE64 = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    private SecretCryptoService crypto;

    @BeforeEach
    void setUp() {
        SecretCryptoService.clearInstanceForTests();
        crypto = new SecretCryptoService(new EncryptionProperties(TEST_KEY_BASE64));
    }

    @AfterEach
    void tearDown() {
        SecretCryptoService.clearInstanceForTests();
    }

    @Test
    void shouldRoundTripEncryptDecrypt() {
        String encrypted = crypto.encrypt("merit-api-key-value");
        assertThat(encrypted).startsWith(SecretCryptoService.PREFIX);
        assertThat(encrypted).doesNotContain("merit-api-key-value");
        assertThat(crypto.decrypt(encrypted)).isEqualTo("merit-api-key-value");
    }

    @Test
    void shouldProduceDifferentCiphertextsForSamePlaintext() {
        String first = crypto.encrypt("same-secret");
        String second = crypto.encrypt("same-secret");
        assertThat(first).isNotEqualTo(second);
        assertThat(crypto.decrypt(first)).isEqualTo(crypto.decrypt(second));
    }

    @Test
    void shouldPassThroughBlankAndNull() {
        assertThat(crypto.encrypt(null)).isNull();
        assertThat(crypto.encrypt("")).isEmpty();
        assertThat(crypto.encrypt("   ")).isEqualTo("   ");
        assertThat(crypto.decrypt(null)).isNull();
    }

    @Test
    void shouldReturnLegacyPlaintextUnchangedOnDecrypt() {
        assertThat(crypto.decrypt("legacy-plaintext")).isEqualTo("legacy-plaintext");
        assertThat(crypto.looksEncrypted("legacy-plaintext")).isFalse();
    }

    @Test
    void shouldFailOnTamperedCiphertext() {
        String encrypted = crypto.encrypt("secret");
        String tampered = encrypted.substring(0, encrypted.length() - 2) + "AA";
        assertThatThrownBy(() -> crypto.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to decrypt");
    }

    @Test
    void shouldRejectMissingKey() {
        SecretCryptoService.clearInstanceForTests();
        assertThatThrownBy(() -> new SecretCryptoService(new EncryptionProperties("")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_ENCRYPTION_KEY");
    }

    @Test
    void shouldRejectWrongKeyLength() {
        SecretCryptoService.clearInstanceForTests();
        // "short" Base64-decoded is not 32 bytes
        assertThatThrownBy(() -> new SecretCryptoService(new EncryptionProperties("c2hvcnQ=")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }
}
