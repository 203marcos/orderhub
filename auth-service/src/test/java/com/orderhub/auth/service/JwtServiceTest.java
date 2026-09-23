package com.orderhub.auth.service;

import com.orderhub.auth.entity.Role;
import com.orderhub.auth.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String TEST_SECRET =
            "dGVzdFNlY3JldEtleUZvckp3dFRlc3RpbmdPcmRlckh1YlByb2plY3QyMDI2IQ==";

    private JwtService jwtService;
    private User testUser;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtService, "expiration", 3600000L);

        testUser = new User("test@orderhub.com", "encoded_pass", "John", "Doe", Role.USER);
        ReflectionTestUtils.setField(testUser, "id", java.util.UUID.randomUUID());
    }

    @Test
    void shouldGenerateNonBlankToken() {
        String token = jwtService.generateToken(testUser);
        assertThat(token).isNotBlank();
    }

    @Test
    void shouldGenerateTokenWithThreeParts() {
        String token = jwtService.generateToken(testUser);
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    void shouldExtractCorrectSubject() {
        String token = jwtService.generateToken(testUser);
        String subject = jwtService.extractSubject(token);
        assertThat(subject).isEqualTo(testUser.getId().toString());
    }

    @Test
    void shouldValidateTokenSuccessfully() {
        String token = jwtService.generateToken(testUser);
        assertThat(jwtService.isTokenValid(token)).isTrue();
    }

    @Test
    void shouldInvalidateTamperedToken() {
        String token = jwtService.generateToken(testUser);
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";
        assertThat(jwtService.isTokenValid(tampered)).isFalse();
    }

    @Test
    void shouldInvalidateExpiredToken() {
        ReflectionTestUtils.setField(jwtService, "expiration", -1000L);
        String token = jwtService.generateToken(testUser);
        assertThat(jwtService.isTokenValid(token)).isFalse();
    }

    @Test
    void shouldIncludeEmailInClaims() {
        String token = jwtService.generateToken(testUser);
        String email = jwtService.extractAllClaims(token).get("email", String.class);
        assertThat(email).isEqualTo(testUser.getEmail());
    }

    @Test
    void shouldIncludeRoleInClaims() {
        String token = jwtService.generateToken(testUser);
        String role = jwtService.extractAllClaims(token).get("role", String.class);
        assertThat(role).isEqualTo("USER");
    }
}
