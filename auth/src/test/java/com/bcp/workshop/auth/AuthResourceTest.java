package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
class AuthResourceTest {
    @Test
    void logsInWithWorkshopUser() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .body("user.username", is("student"))
                .body("token", notNullValue());
    }

    // H-AUTH-01: usuario inexistente debe rechazarse sin emitir token (tasks.md 1.1)
    @Test
    void rejectsUnknownUsername() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"unknown-user\",\"password\":\"anything\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(401)
                .body("token", nullValue());
    }

    // H-AUTH-01: password incorrecta para usuario existente debe rechazarse sin emitir token (tasks.md 1.2)
    @Test
    void rejectsWrongPasswordForExistingUser() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"wrong-password\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(401)
                .body("token", nullValue());
    }

    // H-AUTH-01: carga de inyección en username no debe alterar la condición de búsqueda (tasks.md 1.3)
    @Test
    void rejectsSqlInjectionInUsername() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"' OR '1'='1\",\"password\":\"anything\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(401)
                .body("token", nullValue());
    }

    // H-AUTH-01: carga de inyección en password no debe alterar la condición de búsqueda (tasks.md 1.4)
    @Test
    void rejectsSqlInjectionInPassword() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"' OR '1'='1\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(401)
                .body("token", nullValue());
    }
}
