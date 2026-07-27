package com.orderhub.auth.service;

import com.orderhub.auth.dto.AuthResponse;
import com.orderhub.auth.dto.LoginRequest;
import com.orderhub.auth.dto.RegisterRequest;
import com.orderhub.auth.entity.Role;
import com.orderhub.auth.entity.User;
import com.orderhub.auth.exception.EmailAlreadyExistsException;
import com.orderhub.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;
    @Mock AuthenticationManager authenticationManager;

    @InjectMocks AuthService authService;

    private RegisterRequest registerRequest;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRequest("marcos@orderhub.com", "password123", "Marcos", "Dias");
    }

    @Test
    void shouldRegisterNewUserAndReturnToken() {
        when(userRepository.existsByEmail("marcos@orderhub.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(jwtService.generateToken(any(User.class))).thenReturn("a.jwt.token");
        when(jwtService.getExpiration()).thenReturn(3_600_000L);

        AuthResponse response = authService.register(registerRequest);

        assertThat(response.token()).isEqualTo("a.jwt.token");
        assertThat(response.email()).isEqualTo("marcos@orderhub.com");
        assertThat(response.role()).isEqualTo("USER");
    }

    @Test
    void shouldStoreThePasswordHashedAndNeverInPlainText() {
        when(userRepository.existsByEmail("marcos@orderhub.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(jwtService.generateToken(any(User.class))).thenReturn("a.jwt.token");
        when(jwtService.getExpiration()).thenReturn(3_600_000L);

        authService.register(registerRequest);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPassword())
                .isEqualTo("hashed")
                .isNotEqualTo("password123");
    }

    @Test
    void shouldGiveNewUsersTheUserRoleAndNotAdmin() {
        when(userRepository.existsByEmail("marcos@orderhub.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(jwtService.generateToken(any(User.class))).thenReturn("a.jwt.token");
        when(jwtService.getExpiration()).thenReturn(3_600_000L);

        authService.register(registerRequest);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        // Self-registration must never be a route to elevated privileges.
        assertThat(captor.getValue().getRole()).isEqualTo(Role.USER);
    }

    @Test
    void shouldRejectRegistrationForAnEmailAlreadyTaken() {
        when(userRepository.existsByEmail("marcos@orderhub.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(EmailAlreadyExistsException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldAuthenticateBeforeIssuingATokenOnLogin() {
        User user = new User("marcos@orderhub.com", "hashed", "Marcos", "Dias", Role.USER);
        when(userRepository.findByEmail("marcos@orderhub.com")).thenReturn(Optional.of(user));
        when(jwtService.generateToken(user)).thenReturn("a.jwt.token");
        when(jwtService.getExpiration()).thenReturn(3_600_000L);

        AuthResponse response = authService.login(new LoginRequest("marcos@orderhub.com", "password123"));

        // The credentials must actually be checked; the repository lookup alone proves nothing.
        verify(authenticationManager).authenticate(
                new UsernamePasswordAuthenticationToken("marcos@orderhub.com", "password123"));
        assertThat(response.token()).isEqualTo("a.jwt.token");
    }

    @Test
    void shouldNotIssueATokenWhenAuthenticationFails() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Invalid email or password"));

        assertThatThrownBy(() -> authService.login(new LoginRequest("marcos@orderhub.com", "wrong")))
                .isInstanceOf(BadCredentialsException.class);

        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void shouldFailLoginWhenTheUserVanishedAfterAuthenticating() {
        when(userRepository.findByEmail("ghost@orderhub.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost@orderhub.com", "password123")))
                .isInstanceOf(BadCredentialsException.class);

        verify(jwtService, never()).generateToken(any());
    }
}
