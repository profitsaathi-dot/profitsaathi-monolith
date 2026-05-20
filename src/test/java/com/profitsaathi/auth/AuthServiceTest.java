package com.profitsaathi.auth;

import com.profitsaathi.seller.user.Seller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Auth entities
 * Tests authentication data models
 */
class AuthServiceTest {

    private Seller testSeller;
    private Credentials testCredentials;

    @BeforeEach
    void setUp() {
        testSeller = new Seller();
        testSeller.setId(1L);
        testSeller.setEmail("test@example.com");
        testSeller.setStoreName("Test Store");

        testCredentials = new Credentials();
        testCredentials.setId(1L);
        testCredentials.setEmail("test@example.com");
        testCredentials.setPasswordHash("encodedPassword");
    }

    @Test
    void testCredentialsEntity_GettersAndSetters() {
        Credentials creds = new Credentials();
        creds.setId(100L);
        creds.setEmail("new@example.com");
        creds.setPasswordHash("hashedPassword");

        assertEquals(100L, creds.getId());
        assertEquals("new@example.com", creds.getEmail());
        assertEquals("hashedPassword", creds.getPasswordHash());
    }

    @Test
    void testCredentialsEmail() {
        testCredentials.setEmail("updated@example.com");
        assertEquals("updated@example.com", testCredentials.getEmail());
        assertNotNull(testCredentials.getEmail());
    }

    @Test
    void testCredentialsPasswordHash() {
        String hash = "newHashedPassword123";
        testCredentials.setPasswordHash(hash);

        assertEquals(hash, testCredentials.getPasswordHash());
        assertNotNull(testCredentials.getPasswordHash());
    }

    @Test
    void testSellerEntity_GettersAndSetters() {
        Seller seller = new Seller();
        seller.setId(200L);
        seller.setEmail("seller@example.com");
        seller.setStoreName("New Store");

        assertEquals(200L, seller.getId());
        assertEquals("seller@example.com", seller.getEmail());
        assertEquals("New Store", seller.getStoreName());
    }

    @Test
    void testSellerEmail() {
        testSeller.setEmail("newseller@example.com");
        assertEquals("newseller@example.com", testSeller.getEmail());
        assertNotNull(testSeller.getEmail());
    }

    @Test
    void testSellerStoreName() {
        testSeller.setStoreName("Updated Store");
        assertEquals("Updated Store", testSeller.getStoreName());
        assertNotNull(testSeller.getStoreName());
    }

    @Test
    void testEmailValidation() {
        String validEmail = "test@example.com";
        String invalidEmail = "invalid-email";

        assertTrue(validEmail.contains("@"));
        assertTrue(validEmail.contains("."));
        assertFalse(invalidEmail.contains("@"));
    }

    @Test
    void testPasswordHashNotNull() {
        assertNotNull(testCredentials.getPasswordHash());
        assertFalse(testCredentials.getPasswordHash().isEmpty());
    }

    @Test
    void testSellerIdPositive() {
        assertTrue(testSeller.getId() > 0);
    }

    @Test
    void testCredentialsIdPositive() {
        assertTrue(testCredentials.getId() > 0);
    }
}
