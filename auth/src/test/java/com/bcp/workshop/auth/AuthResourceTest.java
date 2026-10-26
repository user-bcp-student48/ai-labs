package com.bcp.workshop.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
class AuthResourceTest {
    private static final String QUOTED_USERNAME = "fixture'user";
    private static final String QUOTED_PASSWORD = "FixturePass123!";
    private static final String TEST_TOKEN_SECRET = "test-only-auth-signing-key-not-for-production";
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final String DATABASE_URL = System.getenv().getOrDefault(
            "AUTH_DB_URL", "jdbc:sqlite:./data/workshop.db");
    private boolean quotedFixtureInserted;

    @Test
    void logsInWithWorkshopUser() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .body("user.username", is("student"))
                .body("user.displayName", is("Workshop Student"))
                .body("user.role", is("STUDENT"))
                .body("token", notNullValue())
                .body("expiresIn", is(3600))
                .body("debug", is("authenticated-with-legacy-flow"));
    }

    @Test
    void rejectsInvalidCredentials() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"unknown-user\",\"password\":\"WrongPass123!\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(401)
                .body("message", is("Invalid user unknown-user or password"));
    }

    @Test
    void rejectsMissingUsername() {
        given()
                .contentType("application/json")
                .body("{\"password\":\"Workshop123!\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Username is required"));
    }

    @Test
    void rejectsMissingPassword() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Password is required"));
    }

    @Test
    void authenticatesQuotedCredentialAsLiteralValues() throws Exception {
        insertQuotedFixture();

        given()
                .contentType("application/json")
                .body("{\"username\":\"" + QUOTED_USERNAME + "\",\"password\":\"" + QUOTED_PASSWORD + "\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .body("user.username", is(QUOTED_USERNAME))
                .body("user.displayName", is("&lt;Fixture &amp; User&gt;"));
    }

    @Test
    void migratesLegacyPasswordToSaltedHashOnSuccessfulLogin() throws Exception {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(200);

        try (Connection connection = DriverManager.getConnection(DATABASE_URL);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT password FROM legacy_users WHERE username = ?")) {
            statement.setString(1, "student");
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                String storedPassword = result.getString("password");
                assertTrue(storedPassword.startsWith("pbkdf2-sha256$"));
                assertNotEquals("Workshop123!", storedPassword);
            }
        }
    }

    @Test
    void createsSignedTokenWithExplicitClaims() throws Exception {
        String token = given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .extract().path("token");

        String[] tokenParts = token.split("\\.", -1);
        assertEquals(3, tokenParts.length);

        JsonNode header = decodeTokenPart(tokenParts[0]);
        JsonNode claims = decodeTokenPart(tokenParts[1]);
        assertEquals("HS256", header.get("alg").asText());
        assertEquals("JWT", header.get("typ").asText());
        assertEquals("student", claims.get("sub").asText());
        assertEquals("STUDENT", claims.get("role").asText());
        assertTrue(claims.get("exp").asLong() > Instant.now().getEpochSecond());

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(TEST_TOKEN_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] expectedSignature = mac.doFinal(
                (tokenParts[0] + "." + tokenParts[1]).getBytes(StandardCharsets.US_ASCII));
        assertArrayEquals(expectedSignature, Base64.getUrlDecoder().decode(tokenParts[2]));
    }

    @Test
    void rejectsUnconfiguredCorsOrigin() {
        given()
                .header("Origin", "https://client.example.invalid")
                .contentType("application/json")
                .body("{\"username\":\"\",\"password\":\"irrelevant\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(403)
                .header("Access-Control-Allow-Origin", nullValue());
    }

    @Test
    void allowsDevelopmentCorsOrigin() {
        given()
                .header("Origin", "http://localhost:4200")
                .contentType("application/json")
                .body("{\"username\":\"\",\"password\":\"irrelevant\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .header("Access-Control-Allow-Origin", is("http://localhost:4200"));
    }

    @Test
    void returnsGenericErrorWhenAuthenticationFailsUnexpectedly() {
        AuthResource resource = new AuthResource();
        resource.authService = new AuthService() {
            @Override
            public LoginResponse login(String username, String password) {
                throw new IllegalStateException("internal-test-detail");
            }
        };
        LoginRequest request = new LoginRequest();
        request.username = "fixture-user";
        request.password = "FixturePass123!";

        try (Response response = resource.login(request)) {
            Map<?, ?> entity = (Map<?, ?>) response.getEntity();
            assertEquals(500, response.getStatus());
            assertEquals("Internal server error", entity.get("message"));
            assertFalse(entity.containsKey("exception"));
            assertFalse(entity.containsValue("internal-test-detail"));
        }
    }

    @Test
    void returnsBadRequestForNullRequest() {
        AuthResource resource = new AuthResource();

        try (Response response = resource.login(null)) {
            Map<?, ?> entity = (Map<?, ?>) response.getEntity();
            assertEquals(400, response.getStatus());
            assertEquals("Request is null", entity.get("message"));
        }
    }

    @AfterEach
    void removesQuotedFixture() throws Exception {
        if (!quotedFixtureInserted) {
            return;
        }
        try (Connection connection = DriverManager.getConnection(DATABASE_URL);
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM legacy_users WHERE username = ?")) {
            statement.setString(1, QUOTED_USERNAME);
            statement.executeUpdate();
        }
        quotedFixtureInserted = false;
    }

    private void insertQuotedFixture() throws Exception {
        try (Connection connection = DriverManager.getConnection(DATABASE_URL);
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT OR REPLACE INTO legacy_users(username, password, display_name, role) VALUES (?, ?, ?, ?)")) {
            statement.setString(1, QUOTED_USERNAME);
            statement.setString(2, QUOTED_PASSWORD);
            statement.setString(3, "<Fixture & User>");
            statement.setString(4, "STUDENT");
            statement.executeUpdate();
            quotedFixtureInserted = true;
        }
    }

    private static JsonNode decodeTokenPart(String tokenPart) throws Exception {
        byte[] decodedPart = Base64.getUrlDecoder().decode(tokenPart);
        return JSON_MAPPER.readTree(decodedPart);
    }
}
