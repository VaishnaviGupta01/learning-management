package com.lms.entity;

import com.lms.entity.enums.RoleName;

import jakarta.persistence.*;

/**
 * Application role (STUDENT / INSTRUCTOR / ADMIN).
 */
@Entity
@Table(name = "roles")
public class Role extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "name", nullable = false, length = 30, unique = true)
    private RoleName name;

    @Column(name = "description", length = 255)
    private String description;

    public RoleName getName() {
        return name;
    }

    public void setName(RoleName name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
