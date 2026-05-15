package com.profitsaathi.util.aes;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/aes")
@RequiredArgsConstructor
public class AESController {

    private final ObjectMapper objectMapper;
    private final AESService aesService;

    @Value("${aes.secret.key}")
    private String secretKey;

    @PostMapping("/enc")
    public ResponseEntity<String> enc(@RequestBody Object request) {
        try {
            String json = objectMapper.writeValueAsString(request);
            return ResponseEntity.ok("ENC : " + AESUtil.encrypt(json, secretKey));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/decode")
    public ResponseEntity<?> decode(@RequestBody AESRequest request) {
        String decryptedJson = aesService.decryptToJson(request);
        if (decryptedJson == null || decryptedJson.isEmpty()) {
            return ResponseEntity.badRequest().body("Decryption returned empty");
        }
        try {
            Object jsonObject = objectMapper.readValue(decryptedJson, Object.class);
            return ResponseEntity.ok(jsonObject);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Serialization failed");
        }
    }
}
