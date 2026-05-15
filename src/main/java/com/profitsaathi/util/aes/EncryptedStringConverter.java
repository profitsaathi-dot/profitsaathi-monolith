package com.profitsaathi.util.aes;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * JPA attribute converter that AES-encrypts strings on write and decrypts on read.
 * Apply to any column holding sensitive data, e.g. seller bank account number.
 *
 * Static key holder pattern: Hibernate instantiates converters outside the Spring
 * bean lifecycle in some setups, so the resolved key is stashed in a static field
 * via the Spring-managed setter.
 */
@Component
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private static String secretKey;

    @Value("${aes.secret.key}")
    public void setSecretKey(String key) {
        EncryptedStringConverter.secretKey = key;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isBlank()) return null;
        try {
            return AESUtil.encrypt(attribute, secretKey);
        } catch (Exception e) {
            throw new RuntimeException("Field encryption failed", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return null;
        try {
            return AESUtil.decrypt(dbData, secretKey);
        } catch (Exception e) {
            // Likely a key rotation or pre-existing plaintext row — return null
            // rather than crashing every fetch. Operator can re-enter the value.
            return null;
        }
    }
}
