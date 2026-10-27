package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserRepositoryTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void doesNotSeedOrAuthenticateDemoUsersWhenDisabled() throws Exception {
        Class.forName("org.sqlite.JDBC");
        UserRepository repository = new UserRepository();
        repository.databaseUrl = "jdbc:sqlite:" + temporaryDirectory.resolve("production-fixture.db");
        repository.demoUsersEnabled = false;
        repository.studentPassword = Optional.empty();
        repository.trainerPassword = Optional.empty();
        repository.passwordHasher = new PasswordHasher();

        repository.initialize();

        assertEquals(0, countUsers(repository.databaseUrl));
        insertKnownTrainer(repository.databaseUrl);
        assertNull(repository.findByCredentials("trainer", "test-fixture-trainer-password"));
        assertEquals(1, countUsers(repository.databaseUrl));
    }

    @Test
    void requiresNonblankExternalPasswordWhenDemoSeedingIsEnabled() {
        int fixture = 0;
        for (Optional<String> password : List.<Optional<String>>of(
                Optional.empty(),
                Optional.of("  "))) {
            UserRepository repository = new UserRepository();
            repository.databaseUrl = "jdbc:sqlite:"
                    + temporaryDirectory.resolve("missing-demo-password-" + fixture++ + ".db");
            repository.demoUsersEnabled = true;
            repository.studentPassword = password;
            repository.trainerPassword = Optional.empty();
            repository.passwordHasher = new PasswordHasher();

            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    repository::initialize);

            assertTrue(exception.getMessage().contains("auth.demo-users.student-password"));
        }
    }

    private static int countUsers(String databaseUrl) throws Exception {
        try (Connection connection = DriverManager.getConnection(databaseUrl);
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM legacy_users")) {
            result.next();
            return result.getInt(1);
        }
    }

    private static void insertKnownTrainer(String databaseUrl) throws Exception {
        String sql = "INSERT INTO legacy_users(username, password, display_name, role) VALUES (?, ?, ?, ?)";
        try (Connection connection = DriverManager.getConnection(databaseUrl);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, "trainer");
            statement.setString(2, "test-fixture-trainer-password");
            statement.setString(3, "Workshop Trainer");
            statement.setString(4, "TRAINER");
            statement.executeUpdate();
        }
    }
}
