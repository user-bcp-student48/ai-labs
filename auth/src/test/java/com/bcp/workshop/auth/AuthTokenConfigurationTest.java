package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AuthTokenConfigurationTest {
    @Test
    void runtimeSigningSecretComesFromEnvironmentWithoutFallback() throws IOException {
        Properties properties = new Properties();
        try (InputStream configuration = getClass().getResourceAsStream("/application.properties")) {
            assertNotNull(configuration);
            properties.load(configuration);
        }

        assertEquals("${AUTH_TOKEN_SECRET}", properties.getProperty("auth.token.secret"));
    }
}