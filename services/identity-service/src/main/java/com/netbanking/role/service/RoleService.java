package com.netbanking.role.service;

import com.netbanking.common.exception.ResourceNotFoundException;
import com.netbanking.role.domain.Role;
import com.netbanking.role.repository.RoleRepository;
import com.netbanking.user.domain.AppUser;
import com.netbanking.user.repository.AppUserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RoleService {
    public static final String CUSTOMER = "CUSTOMER";
    public static final String ADMIN = "ADMIN";
    private final RoleRepository roleRepository;
    private final AppUserRepository userRepository;

    public RoleService(RoleRepository roleRepository, AppUserRepository userRepository) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
    }

    public void assignDefaultCustomerRole(AppUser user) {
        user.addRole(requireActiveRole(CUSTOMER));
    }

    public void assignRole(Long userId, String roleCode) {
        AppUser user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new ResourceNotFoundException("User was not found."));
        user.addRole(requireActiveRole(roleCode));
    }

    public boolean userHasRole(AppUser user, String roleCode) {
        return user.hasRole(roleCode);
    }

    private Role requireActiveRole(String roleCode) {
        return roleRepository
                .findByRoleCodeAndIsActive(roleCode, "Y")
                .orElseThrow(
                        () ->
                                new ResourceNotFoundException(
                                        "Required role " + roleCode + " was not found."));
    }
}
