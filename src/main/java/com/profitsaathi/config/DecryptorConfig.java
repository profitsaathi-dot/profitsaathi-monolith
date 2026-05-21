package com.profitsaathi.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import javax.sql.DataSource;
import java.util.Base64;
import java.util.Properties;

/**
 * Configuration class for decoding Base64-encoded sensitive values
 * from application.properties
 */
@Configuration
public class DecryptorConfig {

    @Value("${spring.datasource.url}")
    private String url;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String encodedPassword;

    @Value("${spring.mail.password:}")
    private String encodedMailPassword;

    @Value("${spring.mail.from}")
    private String fromMail;

    @Value("${spring.mail.port}")
    private int mailPort;

    @Value("${spring.mail.host}")
    private String mailHost;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${aes.secret.key}")
    private String encodedAesKey;

    @Value("${jwt.secret:}")
    private String encodedJwtSecret;

    /**
     * DataSource bean with decoded database password
     */
    @Bean
    public DataSource dataSource() {
        String decodedPassword = new String(Base64.getDecoder().decode(encodedPassword));
        
        return DataSourceBuilder.create()
                .url(url)
                .username(username)
                .password(decodedPassword)
                .type(HikariDataSource.class)
                .build();
    }

    /**
     * JavaMailSender bean with decoded mail password
     */
    @Bean
    public JavaMailSender javaMailSender() {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(mailHost);
        mailSender.setPort(mailPort);

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.debug", "false"); // Set to true for debugging

        System.out.println("Configuring mail sender for: " + fromMail);

        if (encodedMailPassword != null && !encodedMailPassword.isEmpty()) {
            String decodedMailPassword = new String(Base64.getDecoder().decode(encodedMailPassword));
            
            // Use mailUsername if provided, otherwise use fromMail
            String username = (mailUsername != null && !mailUsername.isEmpty()) ? mailUsername : fromMail;
            
            mailSender.setUsername(username);
            mailSender.setPassword(decodedMailPassword);
            
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
            
            System.out.println("Mail authentication enabled for: " + username);
        } else {
            // No authentication
            props.put("mail.smtp.auth", "false");
            props.put("mail.smtp.starttls.enable", "true");
            
            System.out.println("Mail authentication disabled");
        }

        return mailSender;
    }

    /**
     * AES secret key bean (Base64 encoded, NOT decoded)
     * Used by AESService and EncryptedStringConverter
     * AESUtil expects Base64 and will decode it internally
     */
    @Bean(name = "decodedAesSecretKey")
    public String decodedAesSecretKey() {
        if (encodedAesKey == null || encodedAesKey.isEmpty()) {
            throw new IllegalStateException("AES secret key is not configured in application.properties");
        }
        
        // DO NOT decode! AESUtil expects Base64 and decodes it internally
        // Just verify it's valid Base64 and correct length
        try {
            byte[] decoded = Base64.getDecoder().decode(encodedAesKey);
            if (decoded.length != 32) {
                throw new IllegalStateException("AES key must decode to exactly 32 bytes, got: " + decoded.length);
            }
            System.out.println("AES secret key validated successfully (Base64: " + encodedAesKey.length() + " chars, Decoded: 32 bytes)");
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("AES secret key is not valid Base64: " + e.getMessage());
        }
        
        // Return the Base64 key as-is (AESUtil will decode it)
        return encodedAesKey;
    }

    /**
     * Decoded JWT secret bean (if JWT secret is Base64 encoded)
     * Optional - only if you want to decode JWT secret as well
     */
    @Bean(name = "decodedJwtSecret")
    public String decodedJwtSecret() {
        if (encodedJwtSecret == null || encodedJwtSecret.isEmpty()) {
            System.out.println("JWT secret not configured or not Base64 encoded");
            return encodedJwtSecret; // Return as-is if not encoded
        }
        
        try {
            String decoded = new String(Base64.getDecoder().decode(encodedJwtSecret));
            System.out.println("JWT secret decoded successfully");
            return decoded;
        } catch (IllegalArgumentException e) {
            // Not Base64 encoded, return as-is
            System.out.println("JWT secret is not Base64 encoded, using as-is");
            return encodedJwtSecret;
        }
    }
}
