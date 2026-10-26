package com.bcp.workshop.auth;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.Map;

@Path("/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {
    private static final Logger LOGGER = Logger.getLogger(AuthResource.class);

    @Inject
    AuthService authService;

    @POST
    @Path("/login")
    public Response login(LoginRequest request) {
        if (request == null) {
            return badRequest("Request is null");
        }

        try {
            LoginResponse loginResponse = authService.login(request.username, request.password);
            return Response.ok(loginResponse).build();
        } catch (AuthService.InvalidLoginRequestException error) {
            return badRequest(error.getMessage());
        } catch (AuthService.InvalidCredentialsException error) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of("message", error.getMessage()))
                    .build();
        } catch (Exception error) {
            LOGGER.error("Unable to process login request", error);
            return Response.serverError()
                    .entity(Map.of("message", "Internal server error"))
                    .build();
        }
    }

    private static Response badRequest(String message) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("message", message))
                .build();
    }
}
