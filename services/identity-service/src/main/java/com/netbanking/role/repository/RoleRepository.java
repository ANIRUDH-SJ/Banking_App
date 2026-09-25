package com.netbanking.role.repository;

import com.netbanking.role.domain.Role;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByRoleCodeAndIsActive(String roleCode, String isActive);
}
