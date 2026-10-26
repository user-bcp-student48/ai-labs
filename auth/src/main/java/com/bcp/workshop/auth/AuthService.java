package com.bcp.workshop.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.text.StringEscapeUtils;

@ApplicationScoped
public class AuthService {
    private static final String DEBUG_FLOW = "authenticated-with-legacy-flow";

    @Inject
    UserRepository userRepository;

    @Inject
    SignedTokenService tokenService;

    public LoginResponse login(String username, String password) {
        validateCredentials(username, password);

        UserRecord user = userRepository.findByCredentials(username, password);
        if (user == null) {
            throw new InvalidCredentialsException(username);
        }

        AuthenticatedUser authenticatedUser = new AuthenticatedUser(
                user.username(),
                StringEscapeUtils.escapeHtml4(user.displayName()),
                user.role());
        return new LoginResponse(
                tokenService.create(user.username(), user.role()),
                SignedTokenService.expiresInSeconds(),
                authenticatedUser,
                DEBUG_FLOW);
    }

    private static void validateCredentials(String username, String password) {
        if (username == null || username.trim().isEmpty()) {
            throw new InvalidLoginRequestException("Username is required");
        }
        if (password == null || password.trim().isEmpty()) {
            throw new InvalidLoginRequestException("Password is required");
        }
    }

    public static final class InvalidLoginRequestException extends RuntimeException {
        public InvalidLoginRequestException(String message) {
            super(message);
        }
    }

    public static final class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException(String username) {
            super("Invalid user " + username + " or password");
        }
    }
}
