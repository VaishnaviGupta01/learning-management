package com.lms.seed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.lms.entity.enums.RoleName;
import com.lms.repository.UserRepository;
import com.lms.service.AuthService;

/**
 * Creates the initial ADMIN from ADMIN_EMAIL / ADMIN_PASSWORD if that account does not exist yet.
 * Admins cannot self-register, so this is the bootstrap path. Runs after {@link RoleSeeder}.
 */
@Component
@Order(2)
public class AdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final UserRepository userRepository;
    private final AuthService authService;
    private final String email;
    private final String password;

    public AdminSeeder(UserRepository userRepository, AuthService authService,
                       @Value("${app.admin.email:}") String email,
                       @Value("${app.admin.password:}") String password) {
        this.userRepository = userRepository;
        this.authService = authService;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email.isBlank() || password.isBlank()) {
            log.info("AdminSeeder: ADMIN_EMAIL/ADMIN_PASSWORD not set, skipping");
            return;
        }
        if (userRepository.existsByEmailIgnoreCase(email.trim())) {
            log.info("AdminSeeder: admin {} already present", email);
            return;
        }
        authService.createUser(email, password, "System", "Admin", RoleName.ADMIN);
        log.info("AdminSeeder: created admin {}", email);
    }
}
