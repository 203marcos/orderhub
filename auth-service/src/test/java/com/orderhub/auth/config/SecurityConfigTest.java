package com.orderhub.auth.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * {@link AuthControllerTest} exercises this configuration end-to-end with the authentication
 * manager mocked away, so the {@code authenticationManager} bean method itself never runs there.
 * It only delegates to Spring's own {@link AuthenticationConfiguration}, but that delegation is
 * what lets {@code AuthService} depend on a plain {@link AuthenticationManager} — worth pinning
 * so a future refactor can't quietly build a different one instead.
 */
@ExtendWith(MockitoExtension.class)
class SecurityConfigTest {

    @Mock AuthenticationConfiguration authenticationConfiguration;
    @Mock AuthenticationManager expectedManager;

    // userDetailsService is irrelevant to this bean method; SecurityConfig only needs one to
    // build authenticationProvider(), which this test does not exercise.
    private final SecurityConfig config = new SecurityConfig(mockUserDetailsService());

    private static UserDetailsService mockUserDetailsService() {
        return username -> { throw new UnsupportedOperationException("not used by this test"); };
    }

    @Test
    void shouldExposeTheManagerBuiltBySpringsOwnAuthenticationConfiguration() throws Exception {
        when(authenticationConfiguration.getAuthenticationManager()).thenReturn(expectedManager);

        AuthenticationManager manager = config.authenticationManager(authenticationConfiguration);

        assertThat(manager).isSameAs(expectedManager);
    }
}
