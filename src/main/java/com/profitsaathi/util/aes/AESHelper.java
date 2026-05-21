package com.profitsaathi.util.aes;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public final class AESHelper {

    @Value("${aes.secret.key}")
    private String configuredSecretKey;

    private static String secretKey;

    @PostConstruct
    public void init() {
        secretKey = configuredSecretKey;
        log.info("AESHelper initialized successfully");
    }

    private AESHelper() {
        // Prevent instantiation
    }

    /**
     * Encrypt plain text using AES
     */
    public static String encrypt(String plainText) {

        if (plainText == null || plainText.isBlank()) {
            log.warn("Encryption skipped: input is null or blank");
            return plainText;
        }

        try {
            String encrypted = AESUtil.encrypt(plainText, secretKey);

            log.debug("Text encrypted successfully");

            return encrypted;

        } catch (Exception ex) {

            log.error("Failed to encrypt text", ex);

            // Return original value to avoid breaking application flow
            return plainText;
        }
    }

    /**
     * Decrypt AES encrypted text
     */
    public static String decrypt(String encryptedText) {

        if (encryptedText == null || encryptedText.isBlank()) {
            log.warn("Decryption skipped: input is null or blank");
            return encryptedText;
        }

        try {
            String decrypted = AESUtil.decrypt(encryptedText, secretKey);

            log.debug("Text decrypted successfully");

            return decrypted;

        } catch (Exception ex) {

            log.error("Failed to decrypt text", ex);

            // Return original value to avoid breaking application flow
            return encryptedText;
        }
    }
}