package com.profitsaathi.util.aes;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AESService {

    private final ObjectMapper objectMapper;
    private final String secretKey;

    public AESService(ObjectMapper objectMapper, 
                      @Qualifier("decodedAesSecretKey") String secretKey) {
        this.objectMapper = objectMapper;
        this.secretKey = secretKey;
    }

    public String encrypt(Object data) {
        try {
            return AESUtil.encrypt(objectMapper.writeValueAsString(data), secretKey);
        } catch (Exception e) {
            log.error("Encryption failed: {}", e.getMessage(), e);
            throw new RuntimeException("Encryption failed", e);
        }
    }

    public <T> T decrypt(String encryptedData, Class<T> clazz) {
        try {
            return objectMapper.readValue(AESUtil.decrypt(encryptedData, secretKey), clazz);
        } catch (Exception e) {
            log.error("Decryption failed: {}", e.getMessage(), e);
            throw new RuntimeException("Decryption failed", e);
        }
    }

    public String decryptToJson(AESRequest request) {
        return decryptToJson(request.getRequest());
    }

    public String decryptToJson(String request) {
        try {
            return AESUtil.decrypt(request, secretKey);
        } catch (Exception e) {
            log.error("Decryption failed: {}", e.getMessage(), e);
            throw new RuntimeException("Decryption failed", e);
        }
    }
}
