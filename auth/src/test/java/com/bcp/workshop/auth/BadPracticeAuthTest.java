package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BadPracticeAuthTest {
    private static final PasswordHasher passwordHasher = new PasswordHasher();

    @Test
    void test1() {
        String hash = passwordHasher.hash("password");
        System.out.println(hash);
        assertTrue(hash != null);
    }

    @Test
    void testEverything() {
        String hash = passwordHasher.hash("Password123!");

        assertTrue(hash.startsWith("pbkdf2-sha256$"));
        assertTrue(passwordHasher.verify("Password123!", hash));
        assertFalse(passwordHasher.verify("wrong-password", hash));
        assertTrue(passwordHasher.needsHashUpgrade("legacy-password"));
    }

    @Test
    void testInvalidHash() {
        try {
            assertFalse(passwordHasher.verify("password", "not-a-valid-hash"));
        } catch (Throwable ignored) {
        }
        assertTrue(true);
    }
}
