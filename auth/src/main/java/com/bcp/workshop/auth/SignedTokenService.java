package com.bcp.workshop.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import io.quarkus.runtime.Startup;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

@ApplicationScoped
@Startup
public class SignedTokenService {
    private static final String SIGNING_ALGORITHM = "HmacSHA256";
    private static final String TOKEN_ALGORITHM = "HS256";
    private static final String TOKEN_TYPE = "JWT";
    private static final long TOKEN_LIFETIME_SECONDS = 3600;
    private static final int MINIMUM_SECRET_LENGTH_BYTES = 32;
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    @ConfigProperty(name = "auth.token.secret")
    String secret;

    @PostConstruct
    void validateConfiguredSecret() {
        validateSecret();
    }

    public String create(String username, String role) {
        validateSecret();
        long expiry = Instant.now().plusSeconds(TOKEN_LIFETIME_SECONDS).getEpochSecond();
        String encodedHeader = encodeJson(Map.of("alg", TOKEN_ALGORITHM, "typ", TOKEN_TYPE));
        String encodedPayload = encodeJson(Map.of("sub", username, "role", role, "exp", expiry));
        String signingInput = encodedHeader + "." + encodedPayload;
        return signingInput + "." + sign(signingInput);
    }

    public static long expiresInSeconds() {
        return TOKEN_LIFETIME_SECONDS;
    }

    private static String encodeJson(Map<String, ?> claims) {
        try {
            return ENCODER.encodeToString(JSON_MAPPER.writeValueAsBytes(claims));
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Unable to serialize token claims", error);
        }
    }

    private String sign(String signingInput) {
        try {
            Mac mac = newSigningMac();
            byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
            mac.init(new SecretKeySpec(secretBytes, SIGNING_ALGORITHM));
            return ENCODER.encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII)));
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("Unable to sign authentication token", error);
        }
    }

    Mac newSigningMac() throws GeneralSecurityException {
        return Mac.getInstance(SIGNING_ALGORITHM);
    }

    private void validateSecret() {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MINIMUM_SECRET_LENGTH_BYTES) {
            throw new IllegalStateException("Authentication token secret must contain at least 32 bytes");
        }
    }
}
