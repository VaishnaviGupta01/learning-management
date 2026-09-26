package com.lms.seed;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.lms.entity.Role;
import com.lms.entity.enums.RoleName;
import com.lms.repository.RoleRepository;

/**
 * Ensures every {@link RoleName} exists. Safe to run on every startup.
 */
@Component
@Order(1)
public class RoleSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RoleSeeder.class);

    private static final Map<RoleName, String> DESCRIPTIONS = Map.of(
            RoleName.STUDENT, "Enrols in courses, takes quizzes and receives recommendations",
            RoleName.INSTRUCTOR, "Creates courses, content and questions; reviews AI output",
            RoleName.ADMIN, "Manages users, roles and system settings");

    private final RoleRepository roleRepository;

    public RoleSeeder(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int created = 0;
        for (RoleName name : RoleName.values()) {
            if (!roleRepository.existsByName(name)) {
                Role role = new Role();
                role.setName(name);
                role.setDescription(DESCRIPTIONS.get(name));
                roleRepository.save(role);
                created++;
            }
        }
        log.info("RoleSeeder: {} role(s) created, {} already present", created, RoleName.values().length - created);
    }
}
