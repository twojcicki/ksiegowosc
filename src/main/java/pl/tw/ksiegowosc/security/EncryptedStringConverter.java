package pl.tw.ksiegowosc.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import org.springframework.util.StringUtils;

@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (!StringUtils.hasText(attribute)) {
            return attribute;
        }
        return SecretCryptoService.requireInstance().encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (!StringUtils.hasText(dbData)) {
            return dbData;
        }
        SecretCryptoService crypto = SecretCryptoService.requireInstance();
        if (crypto.looksEncrypted(dbData)) {
            return crypto.decrypt(dbData);
        }
        return dbData;
    }
}
