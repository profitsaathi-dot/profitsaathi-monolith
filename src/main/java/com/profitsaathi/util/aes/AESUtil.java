package com.profitsaathi.util.aes;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

    public final class AESUtil {

        // GCM provides authenticated encryption (AEAD)
        private static final String ALGORITHM = "AES/GCM/NoPadding";

        // NIST recommends a 12-byte (96-bit) IV for GCM for performance and security
        private static final int IV_LENGTH_BYTES = 12;

        // The authentication tag length in bits (128 bits is the standard maximum strength)
        private static final int TAG_LENGTH_BITS = 128;

        // SecureRandom is thread-safe; reuse the instance
        private static final SecureRandom SECURE_RANDOM = new SecureRandom();

        private AESUtil() {}

        /**
         * Encrypts plain text using AES-GCM-256.
         * @param data The raw string to encrypt
         * @param base64Key The 256-bit (32-byte) secret key string
         * @return Base64 encoded string containing [IV] + [Ciphertext + Auth Tag]
         */
        public static String encrypt(String data, String base64Key) throws Exception {
            byte[] keyBytes = Base64.getDecoder().decode(base64Key);
            if (keyBytes.length != 32) {
                throw new IllegalArgumentException("Key must be exactly 256 bits (32 bytes) long for AES-256.");
            }
            SecretKey secretKey = new SecretKeySpec(keyBytes, "AES");

            // 1. Generate a unique IV for every single encryption event
            byte[] iv = new byte[IV_LENGTH_BYTES];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            byte[] encrypted = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));

            // 2. Combine IV and encrypted data (which includes the authentication tag at the end)
            byte[] encryptedBuffer = ByteBuffer.allocate(iv.length + encrypted.length)
                    .put(iv)
                    .put(encrypted)
                    .array();

            return Base64.getEncoder().encodeToString(encryptedBuffer);
        }

        /**
         * Decrypts a Base64 encoded AES-GCM-256 string.
         * @param encryptedData Base64 encoded string containing [IV] + [Ciphertext + Auth Tag]
         * @param base64Key The 256-bit (32-byte) secret key string
         * @return The original decrypted string
         */
        public static String decrypt(String encryptedData, String base64Key) throws Exception {
            byte[] keyBytes = Base64.getDecoder().decode(base64Key);
            if (keyBytes.length != 32) {
                throw new IllegalArgumentException("Key must be exactly 256 bits (32 bytes) long for AES-256.");
            }
            SecretKey secretKey = new SecretKeySpec(keyBytes, "AES");

            byte[] decoded = Base64.getDecoder().decode(encryptedData);
            if (decoded.length < IV_LENGTH_BYTES + (TAG_LENGTH_BITS / 8)) {
                throw new IllegalArgumentException("Ciphertext is too short or corrupted.");
            }

            // 1. Extract the IV from the payload
            byte[] iv = new byte[IV_LENGTH_BYTES];
            System.arraycopy(decoded, 0, iv, 0, iv.length);

            // 2. Extract the actual ciphertext + authentication tag
            int cipherTextLength = decoded.length - iv.length;
            byte[] cipherText = new byte[cipherTextLength];
            System.arraycopy(decoded, iv.length, cipherText, 0, cipherTextLength);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            // doFinal will automatically verify the 128-bit authentication tag.
            // If data was tampered with, it throws an AEADBadTagException.
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        }
    }
