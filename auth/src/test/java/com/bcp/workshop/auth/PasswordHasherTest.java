package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {
    private final PasswordHasher passwordHasher = new PasswordHasher();

    @Test
    void verifiesPasswordHashAndRejectsWrongPassword() {
        String passwordHash = passwordHasher.hash("FixturePass123!");

        assertTrue(passwordHasher.verify("FixturePass123!", passwordHash));
        assertFalse(passwordHasher.verify("WrongPass123!", passwordHash));
        assertFalse(passwordHasher.needsHashUpgrade(passwordHash));
    }

    @Test
    void createsDifferentHashesForSamePassword() {
        String firstHash = passwordHasher.hash("FixturePass123!");
        String secondHash = passwordHasher.hash("FixturePass123!");

        assertNotEquals(firstHash, secondHash);
    }

    @Test
    void recognizesLegacyPlainTextForLazyMigration() {
        assertTrue(passwordHasher.verify("FixturePass123!", "FixturePass123!"));
        assertTrue(passwordHasher.needsHashUpgrade("FixturePass123!"));
    }

    @Test
    void rejectsMalformedPasswordHash() {
        assertFalse(passwordHasher.verify("FixturePass123!", "pbkdf2-sha256$600000$bad$bad"));
    }

    @Test
    void rejectsPasswordHashWithInvalidIterationCount() {
        assertFalse(passwordHasher.verify(
                "FixturePass123!", "pbkdf2-sha256$not-a-number$AAAAAAAAAAAAAAAAAAAAAA==$AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="));
    }

    @Test
    void rejectsPasswordHashWithMissingFields() {
        assertFalse(passwordHasher.verify("FixturePass123!", "pbkdf2-sha256$600000"));
    }
}
