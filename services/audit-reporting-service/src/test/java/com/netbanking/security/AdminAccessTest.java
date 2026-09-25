package com.netbanking.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.netbanking.admin.api.AdminAuditController;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;

class AdminAccessTest {
    @Test
    void auditSearchRemainsRestrictedToAdministrators() {
        PreAuthorize authorization = AdminAuditController.class.getAnnotation(PreAuthorize.class);

        assertThat(authorization).isNotNull();
        assertThat(authorization.value()).isEqualTo("hasRole('ADMIN')");
        assertThat(AdminAuditController.class.getAnnotation(Profile.class)).isNull();
    }
}
