package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class SignedTokenServiceTest {
    @Test
    void rejectsMissingSigningSecret() {
        SignedTokenService tokenService = new SignedTokenService();
        tokenService.secret = null;

        assertThrows(
                IllegalStateException.class,
                tokenService::validateConfiguredSecret);
    }

    @Test
    void rejectsSigningSecretShorterThanMinimumLength() {
        SignedTokenService tokenService = new SignedTokenService();
        tokenService.secret = "test-secret-too-short";

        assertThrows(
                IllegalStateException.class,
                () -> tokenService.create("fixture-user", "STUDENT"));
    }
}
