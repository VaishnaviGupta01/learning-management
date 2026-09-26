package com.lms;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;

import com.lms.entity.enums.RoleName;
import com.lms.repository.RoleRepository;
import com.lms.seed.RoleSeeder;

@SpringBootTest
class LmsApplicationTests {

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private RoleSeeder roleSeeder;

    @Test
    void contextLoadsAndRolesAreSeeded() {
        assertThat(roleRepository.count()).isEqualTo(RoleName.values().length);
        for (RoleName name : RoleName.values()) {
            assertThat(roleRepository.findByName(name)).isPresent();
        }
    }

    @Test
    void roleSeederIsIdempotent() {
        ApplicationArguments args = new DefaultApplicationArguments();
        roleSeeder.run(args);
        roleSeeder.run(args);
        assertThat(roleRepository.count()).isEqualTo(RoleName.values().length);
    }
}
