package com.orderhub.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderhub.auth.config.SecurityConfig;
import com.orderhub.auth.dto.AuthResponse;
import com.orderhub.auth.dto.LoginRequest;
import com.orderhub.auth.dto.RegisterRequest;
import com.orderhub.auth.exception.EmailAlreadyExistsException;
import com.orderhub.auth.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The HTTP edge of auth-service.
 *
 * <p>{@link SecurityConfig} is imported rather than mocked away, so this also proves that
 * {@code /auth/**} really is reachable without a token. If someone tightened that rule by
 * accident, registration would start returning 401 and only an end-to-end test would notice.
 */
@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
@DisplayName("AuthController")
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean AuthService authService;

    // Required by SecurityConfig; the real ones need a database.
    @MockitoBean UserDetailsService userDetailsService;
    @MockitoBean AuthenticationManager authenticationManager;

    private AuthResponse aToken() {
        return AuthResponse.of("a.jwt.token", 3_600_000L, "marcos@orderhub.com", "USER");
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    @Nested
    @DisplayName("POST /auth/register")
    class Register {

        @Test
        @DisplayName("returns 201 with a token, reachable without authentication")
        void shouldReturn201() throws Exception {
            when(authService.register(any())).thenReturn(aToken());

            mockMvc.perform(post("/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new RegisterRequest(
                                    "marcos@orderhub.com", "password123", "Marcos", "Dias"))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.token").value("a.jwt.token"))
                    .andExpect(jsonPath("$.role").value("USER"));
        }

        @Test
        @DisplayName("returns 409 when the email is already taken")
        void shouldReturn409() throws Exception {
            when(authService.register(any()))
                    .thenThrow(new EmailAlreadyExistsException("marcos@orderhub.com"));

            mockMvc.perform(post("/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new RegisterRequest(
                                    "marcos@orderhub.com", "password123", "Marcos", "Dias"))))
                    .andExpect(status().isConflict());
        }

        @ParameterizedTest(name = "email=''{0}'' password=''{1}''")
        @CsvSource({
                "not-an-email,          password123",
                "'',                    password123",
                "marcos@orderhub.com,   short",
                "marcos@orderhub.com,   ''"
        })
        @DisplayName("returns 400 for an invalid registration")
        void shouldRejectInvalidRegistrations(String email, String password) throws Exception {
            mockMvc.perform(post("/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new RegisterRequest(email, password, "Marcos", "Dias"))))
                    .andExpect(status().isBadRequest());

            // A malformed request must never reach the service, let alone the database.
            verifyNoInteractions(authService);
        }
    }

    @Nested
    @DisplayName("POST /auth/login")
    class Login {

        @Test
        @DisplayName("returns 200 with a token")
        void shouldReturn200() throws Exception {
            when(authService.login(any())).thenReturn(aToken());

            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new LoginRequest("marcos@orderhub.com", "password123"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tokenType").value("Bearer"));
        }

        @Test
        @DisplayName("returns 401 without revealing whether the account exists")
        void shouldReturn401WithAGenericMessage() throws Exception {
            when(authService.login(any()))
                    .thenThrow(new BadCredentialsException("No user found for ghost@orderhub.com"));

            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new LoginRequest("ghost@orderhub.com", "whatever"))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value("Invalid email or password"));
        }
    }
}
