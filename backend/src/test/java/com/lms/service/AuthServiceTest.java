package com.lms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.lms.dto.auth.LoginRequest;
import com.lms.dto.auth.RegisterRequest;
import com.lms.entity.Role;
import com.lms.entity.User;
import com.lms.entity.enums.RoleName;
import com.lms.exception.BadRequestException;
import com.lms.exception.ConflictException;
import com.lms.repository.RoleRepository;
import com.lms.repository.UserRepository;
import com.lms.security.JwtService;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuthenticationManager authenticationManager;
    @Mock JwtService jwtService;
    @InjectMocks AuthService authService;

    @Test
    void registerNormalisesEmailHashesPasswordAndDefaultsToStudent() {
        Role student = role(RoleName.STUDENT);
        when(userRepository.existsByEmailIgnoreCase("sam@x.io")).thenReturn(false);
        when(roleRepository.findByName(RoleName.STUDENT)).thenReturn(Optional.of(student));
        when(passwordEncoder.encode("Password123!")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.generateToken(any())).thenReturn("jwt");

        var response = authService.register(new RegisterRequest("  Sam@X.io ", "Password123!", " Sam ", "Lee", null));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("sam@x.io");
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("hashed");
        assertThat(saved.getValue().getFirstName()).isEqualTo("Sam");
        assertThat(saved.getValue().getRole().getName()).isEqualTo(RoleName.STUDENT);
        assertThat(response.token()).isEqualTo("jwt");
    }

    @Test
    void duplicateEmailIsRejectedWithoutSaving() {
        when(userRepository.existsByEmailIgnoreCase("sam@x.io")).thenReturn(true);
        assertThatThrownBy(() -> authService.register(new RegisterRequest("sam@x.io", "Password123!", "S", "L", null)))
                .isInstanceOf(ConflictException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void adminSelfRegistrationIsRejectedEvenIfValidationWasBypassed() {
        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("a@x.io", "Password123!", "A", "B", RoleName.ADMIN)))
                .isInstanceOf(BadRequestException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void badCredentialsPropagateAndNoTokenIsIssued() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));
        assertThatThrownBy(() -> authService.login(new LoginRequest("sam@x.io", "wrong")))
                .isInstanceOf(BadCredentialsException.class);
        verify(jwtService, never()).generateToken(any());
    }

    private static Role role(RoleName name) {
        Role r = new Role();
        r.setName(name);
        return r;
    }
}
