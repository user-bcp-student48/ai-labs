package com.bcp.workshop.auth;

public record LoginResponse(String token, long expiresIn, AuthenticatedUser user, String debug) {}
