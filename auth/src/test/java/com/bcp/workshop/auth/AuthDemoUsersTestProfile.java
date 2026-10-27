package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

public final class AuthDemoUsersTestProfile implements QuarkusTestProfile {
    static final String STUDENT_PASSWORD = "test-student-demo-password";
    static final String TRAINER_PASSWORD = "test-trainer-demo-password";

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "auth.demo-users.student-password", STUDENT_PASSWORD,
                "auth.demo-users.trainer-password", TRAINER_PASSWORD);
    }
}
