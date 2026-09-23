package com.orderhub.auth.integration;

import com.orderhub.auth.dto.AuthResponse;
import com.orderhub.auth.dto.LoginRequest;
import com.orderhub.auth.dto.RegisterRequest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Tag("integration")
class AuthIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void shouldRegisterNewUserAndReturnToken() {
        RegisterRequest request = new RegisterRequest(
                "marcos@orderhub.com", "password123", "Marcos", "Dias");

        ResponseEntity<AuthResponse> response =
                restTemplate.postForEntity("/auth/register", request, AuthResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().token()).isNotBlank();
        assertThat(response.getBody().tokenType()).isEqualTo("Bearer");
        assertThat(response.getBody().email()).isEqualTo("marcos@orderhub.com");
        assertThat(response.getBody().role()).isEqualTo("USER");
    }

    @Test
    void shouldLoginAfterRegistration() {
        RegisterRequest register = new RegisterRequest(
                "login.test@orderhub.com", "password123", "Login", "Test");
        restTemplate.postForEntity("/auth/register", register, AuthResponse.class);

        LoginRequest login = new LoginRequest("login.test@orderhub.com", "password123");
        ResponseEntity<AuthResponse> response =
                restTemplate.postForEntity("/auth/login", login, AuthResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().token()).isNotBlank();
    }

    @Test
    void shouldReturn409WhenEmailAlreadyExists() {
        RegisterRequest request = new RegisterRequest(
                "duplicate@orderhub.com", "password123", "Dup", "User");
        restTemplate.postForEntity("/auth/register", request, AuthResponse.class);

        ResponseEntity<String> second =
                restTemplate.postForEntity("/auth/register", request, String.class);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void shouldReturn401ForWrongPassword() {
        RegisterRequest register = new RegisterRequest(
                "wrong.pass@orderhub.com", "password123", "Wrong", "Pass");
        restTemplate.postForEntity("/auth/register", register, AuthResponse.class);

        LoginRequest badLogin = new LoginRequest("wrong.pass@orderhub.com", "wrongpassword");
        ResponseEntity<String> response =
                restTemplate.postForEntity("/auth/login", badLogin, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldReturn400ForInvalidEmail() {
        RegisterRequest request = new RegisterRequest(
                "not-an-email", "password123", "Bad", "Email");
        ResponseEntity<String> response =
                restTemplate.postForEntity("/auth/register", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
