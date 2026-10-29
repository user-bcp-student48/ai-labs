package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SignedTokenServiceTest {
    @Test
    void rejectsMissingSigningSecret() {
        SignedTokenService tokenService = new SignedTokenService();
        tokenService.secret = null;

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                tokenService::validateConfiguredSecret);
        assertEquals("Authentication token secret must contain at least 32 bytes", error.getMessage());
    }

    @Test
    void rejectsSigningSecretShorterThanMinimumLength() {
        SignedTokenService tokenService = new SignedTokenService();
        String shortSecret = "\u00e9".repeat(15) + "a";
        tokenService.secret = shortSecret;

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> tokenService.create("fixture-user", "STUDENT"));
        assertFalse(error.getMessage().contains(shortSecret));
    }

    @Test
    void acceptsSigningSecretWithExactlyThirtyTwoUtf8Bytes() {
        SignedTokenService tokenService = new SignedTokenService();
        tokenService.secret = "\u00e9".repeat(16);

        assertNotNull(tokenService.create("fixture-user", "STUDENT"));
    }

    @Test
    void doesNotExposeSecretWhenSigningFails() {
        String signingSecret = "synthetic-token-signing-secret-for-tests";
        SignedTokenService tokenService = new SignedTokenService() {
            @Override
            Mac newSigningMac() throws GeneralSecurityException {
                throw new NoSuchAlgorithmException("Synthetic signing failure");
            }
        };
        tokenService.secret = signingSecret;

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> tokenService.create("fixture-user", "STUDENT"));

        assertEquals("Unable to sign authentication token", error.getMessage());
        assertFalse(error.getMessage().contains(signingSecret));
        assertNotNull(error.getCause());
        assertFalse(error.getCause().getMessage().contains(signingSecret));
    }
}
