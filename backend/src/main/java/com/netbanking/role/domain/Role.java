package com.netbanking.role.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "role")
public class Role {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "role_id") private Long roleId;
    @Column(name = "role_code", nullable = false, unique = true) private String roleCode;
    @Column(name = "role_name", nullable = false, unique = true) private String roleName;
    @Column(name = "is_active", nullable = false, columnDefinition = "CHAR(1)") private String isActive;

    protected Role() { }
    public Long getRoleId() { return roleId; }
    public String getRoleCode() { return roleCode; }
    public String getRoleName() { return roleName; }
    public boolean isActive() { return "Y".equals(isActive); }
}
