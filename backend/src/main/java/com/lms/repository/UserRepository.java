package com.lms.repository;

import java.util.List;
import java.util.Optional;

import com.lms.entity.User;
import com.lms.entity.enums.RoleName;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    List<User> findByRoleName(RoleName roleName);
    long countByRoleName(RoleName roleName);
}
