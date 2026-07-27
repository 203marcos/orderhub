package com.orderhub.auth.service;

import com.orderhub.auth.entity.Role;
import com.orderhub.auth.entity.User;
import com.orderhub.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {

    @Mock UserRepository userRepository;

    @InjectMocks UserDetailsServiceImpl userDetailsService;

    @Test
    void shouldLoadUserByEmail() {
        User user = new User("marcos@orderhub.com", "hashed", "Marcos", "Dias", Role.USER);
        when(userRepository.findByEmail("marcos@orderhub.com")).thenReturn(Optional.of(user));

        UserDetails details = userDetailsService.loadUserByUsername("marcos@orderhub.com");

        assertThat(details.getUsername()).isEqualTo("marcos@orderhub.com");
        assertThat(details.getPassword()).isEqualTo("hashed");
    }

    @Test
    void shouldThrowUsernameNotFoundForAnUnknownEmail() {
        when(userRepository.findByEmail("ghost@orderhub.com")).thenReturn(Optional.empty());

        // Must be UsernameNotFoundException specifically: Spring Security maps it to a generic
        // bad-credentials failure, so the response cannot reveal whether the email exists.
        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("ghost@orderhub.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
