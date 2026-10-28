package com.bcp.workshop.auth;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import java.util.Set;

@ApplicationScoped
public class UserRepository {
    private static final String STUDENT_PASSWORD_PROPERTY = "auth.demo-users.student-password";
    private static final String TRAINER_PASSWORD_PROPERTY = "auth.demo-users.trainer-password";
    private static final String STUDENT_USERNAME = "student";
    private static final String TRAINER_USERNAME = "trainer";
    private static final Set<String> DEMO_USERNAMES = Set.of(STUDENT_USERNAME, TRAINER_USERNAME);

    @ConfigProperty(name = "auth.database.url")
    String databaseUrl;

    @ConfigProperty(name = STUDENT_PASSWORD_PROPERTY)
    Optional<String> studentPassword;

    @ConfigProperty(name = TRAINER_PASSWORD_PROPERTY)
    Optional<String> trainerPassword;

    @ConfigProperty(name = "auth.demo-users.enabled", defaultValue = "false")
    boolean demoUsersEnabled;

    @Inject
    PasswordHasher passwordHasher;

    @PostConstruct
    void initialize() {
        try {
            Files.createDirectories(Path.of("data"));
            try (Connection connection = DriverManager.getConnection(databaseUrl);
                 Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS legacy_users (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE, password TEXT NOT NULL, display_name TEXT NOT NULL, role TEXT NOT NULL)");
            }
            if (demoUsersEnabled) {
                seedDemoUsers();
            }
        } catch (IOException | SQLException error) {
            throw new IllegalStateException("Unable to initialize the authentication database", error);
        }
    }

    public UserRecord findByCredentials(String username, String password) {
        if (!demoUsersEnabled && isDemoUsername(username)) {
            return null;
        }

        String sql = "SELECT id, username, password, display_name, role FROM legacy_users WHERE username = ?";
        try (Connection connection = DriverManager.getConnection(databaseUrl);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            UserRecord user;
            long userId;
            String storedPassword;
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }

                storedPassword = result.getString("password");
                if (!passwordHasher.verify(password, storedPassword)) {
                    return null;
                }

                userId = result.getLong("id");
                user = new UserRecord(
                        userId,
                        result.getString("username"),
                        result.getString("display_name"),
                        result.getString("role"));
            }
            if (passwordHasher.needsHashUpgrade(storedPassword)) {
                updatePasswordHash(connection, userId, storedPassword, passwordHasher.hash(password));
            }
            return user;
        } catch (SQLException error) {
            throw new IllegalStateException("Unable to query authentication database", error);
        }
    }

    private void seedUser(String username, String password, String displayName, String role)
            throws SQLException {
        String sql = "INSERT OR IGNORE INTO legacy_users(username, password, display_name, role) VALUES (?, ?, ?, ?)";
        try (Connection connection = DriverManager.getConnection(databaseUrl);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.setString(2, passwordHasher.hash(password));
            statement.setString(3, displayName);
            statement.setString(4, role);
            statement.executeUpdate();
        }
    }

    private void seedDemoUsers() throws SQLException {
        seedUser(
                STUDENT_USERNAME,
                requireDemoPassword(studentPassword, STUDENT_PASSWORD_PROPERTY),
                "Workshop Student",
                "STUDENT");
        seedUser(
                TRAINER_USERNAME,
                requireDemoPassword(trainerPassword, TRAINER_PASSWORD_PROPERTY),
                "Workshop Trainer",
                "TRAINER");
    }

    private static String requireDemoPassword(Optional<String> password, String propertyName) {
        return password
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> new IllegalStateException(
                        "Demo user seeding requires configuration property " + propertyName));
    }

    private static boolean isDemoUsername(String username) {
        return username != null && DEMO_USERNAMES.contains(username);
    }

    private void updatePasswordHash(
            Connection connection, long userId, String currentPassword, String passwordHash)
            throws SQLException {
        String sql = "UPDATE legacy_users SET password = ? WHERE id = ? AND password = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, passwordHash);
            statement.setLong(2, userId);
            statement.setString(3, currentPassword);
            statement.executeUpdate();
        }
    }
}
