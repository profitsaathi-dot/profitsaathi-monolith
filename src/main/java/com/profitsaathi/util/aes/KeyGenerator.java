package com.profitsaathi.util.aes;

import java.security.SecureRandom;
import java.util.Base64;

public class KeyGenerator {
    public static void main(String[] args) {
        byte[] randomKeyBytes = new byte[32]; // 32 bytes = 256 bits
        new SecureRandom().nextBytes(randomKeyBytes);
        String base64Key = Base64.getEncoder().encodeToString(randomKeyBytes);

        System.out.println("Your 256-bit Base64 Key: " + base64Key);
    }
}
