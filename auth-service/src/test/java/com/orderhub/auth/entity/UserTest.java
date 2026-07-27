package com.orderhub.auth.entity;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link User#getAuthorities()} is the one piece of real mapping logic on this entity: it is
 * how a persisted {@link Role} becomes the {@code ROLE_*} authority Spring Security checks
 * against. Everything else on this class is a plain getter.
 */
class UserTest {

    @Test
    void shouldExposeUserRoleAsASpringSecurityAuthority() {
        User user = new User("marcos@orderhub.com", "hashed", "Marcos", "Dias", Role.USER);

        assertThat(user.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");
    }

    @Test
    void shouldExposeAdminRoleAsASpringSecurityAuthority() {
        User user = new User("admin@orderhub.com", "hashed", "Marcos", "Dias", Role.ADMIN);

        // Distinguishing the two matters: this string is what @PreAuthorize("hasRole('ADMIN')")
        // actually compares against.
        assertThat(user.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    void shouldExposeEmailAsTheSpringSecurityUsername() {
        User user = new User("marcos@orderhub.com", "hashed", "Marcos", "Dias", Role.USER);

        // Login is by email, so this is what authentication actually keys on.
        assertThat(user.getUsername()).isEqualTo("marcos@orderhub.com");
    }
}
