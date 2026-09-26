package com.lms.repository;

import java.util.Optional;

import com.lms.entity.Role;
import com.lms.entity.enums.RoleName;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(RoleName name);
    boolean existsByName(RoleName name);
}
