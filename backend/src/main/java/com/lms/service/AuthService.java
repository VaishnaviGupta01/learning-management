package com.lms.service;

import java.time.Instant;
import java.util.Locale;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lms.dto.auth.AuthResponse;
import com.lms.dto.auth.LoginRequest;
import com.lms.dto.auth.RegisterRequest;
import com.lms.dto.auth.UserResponse;
import com.lms.entity.Role;
import com.lms.entity.User;
import com.lms.entity.enums.RoleName;
import com.lms.exception.BadRequestException;
import com.lms.exception.ConflictException;
import com.lms.exception.ResourceNotFoundException;
import com.lms.repository.RoleRepository;
import com.lms.repository.UserRepository;
import com.lms.security.JwtService;
import com.lms.security.UserPrincipal;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder, AuthenticationManager authenticationManager,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        RoleName roleName = request.effectiveRole();
        if (roleName == RoleName.ADMIN) {
            // Defence in depth: the DTO's @AssertTrue already rejects this.
            throw new BadRequestException("Self-registration as ADMIN is not allowed");
        }
        User user = createUser(request.email(), request.password(), request.firstName(), request.lastName(), roleName);
        return issueToken(user);
    }

    /** Creates a user with a hashed password. Shared by registration and the admin seeder. */
    @Transactional
    public User createUser(String email, String rawPassword, String firstName, String lastName, RoleName roleName) {
        String normalizedEmail = normalize(email);
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ConflictException("An account with this email already exists");
        }
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalStateException("Role not seeded: " + roleName));

        User user = new User();
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setFirstName(firstName.trim());
        user.setLastName(lastName.trim());
        user.setRole(role);
        user.setEnabled(true);
        return userRepository.save(user);
    }

    /** Throws an AuthenticationException (mapped to 401) on bad credentials or a disabled account. */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = normalize(request.email());
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, request.password()));
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));
        user.setLastLoginAt(Instant.now());
        return issueToken(user);
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(Long userId) {
        return userRepository.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    private AuthResponse issueToken(User user) {
        String token = jwtService.generateToken(UserPrincipal.from(user));
        return AuthResponse.bearer(token, jwtService.getExpirationMs(), UserResponse.from(user));
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
