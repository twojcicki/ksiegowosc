package pl.tw.ksiegowosc.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pl.tw.ksiegowosc.config.EncryptionProperties;

class EncryptedStringConverterTest {

    private static final String TEST_KEY_BASE64 = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    private EncryptedStringConverter converter;

    @BeforeEach
    void setUp() {
        SecretCryptoService.clearInstanceForTests();
        new SecretCryptoService(new EncryptionProperties(TEST_KEY_BASE64));
        converter = new EncryptedStringConverter();
    }

    @AfterEach
    void tearDown() {
        SecretCryptoService.clearInstanceForTests();
    }

    @Test
    void shouldEncryptOnWriteAndDecryptOnRead() {
        String dbValue = converter.convertToDatabaseColumn("client-secret");
        assertThat(dbValue).startsWith(SecretCryptoService.PREFIX);
        assertThat(converter.convertToEntityAttribute(dbValue)).isEqualTo("client-secret");
    }

    @Test
    void shouldPassThroughLegacyPlaintextOnRead() {
        assertThat(converter.convertToEntityAttribute("old-plaintext")).isEqualTo("old-plaintext");
    }

    @Test
    void shouldPassThroughNullAndBlank() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToDatabaseColumn("")).isEmpty();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
