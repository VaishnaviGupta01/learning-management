package com.lms.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.lms.entity.User;
import com.lms.entity.enums.RoleName;

/**
 * Authenticated user as seen by Spring Security. Inject into controllers with
 * {@code @AuthenticationPrincipal UserPrincipal principal}.
 */
public class UserPrincipal implements UserDetails {

    private final Long id;
    private final String email;
    private final String passwordHash;
    private final RoleName role;
    private final boolean enabled;

    public UserPrincipal(Long id, String email, String passwordHash, RoleName role, boolean enabled) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.enabled = enabled;
    }

    public static UserPrincipal from(User user) {
        return new UserPrincipal(user.getId(), user.getEmail(), user.getPasswordHash(),
                user.getRole().getName(), user.isEnabled());
    }

    public Long getId() {
        return id;
    }

    public RoleName getRole() {
        return role;
    }

    public boolean isAdmin() {
        return role == RoleName.ADMIN;
    }

    public boolean isInstructor() {
        return role == RoleName.INSTRUCTOR;
    }

    public boolean isStudent() {
        return role == RoleName.STUDENT;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
