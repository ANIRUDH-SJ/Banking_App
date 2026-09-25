package com.netbanking.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.netbanking.admin.api.AdminAuditController;
import com.netbanking.notification.api.NotificationController;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;

class AccessControlContractTest {

    @Test
    void notificationApiDoesNotExposePublicCreationAndIsNotProfileGated() {
        boolean exposesPost = Arrays.stream(NotificationController.class.getDeclaredMethods())
                .anyMatch(method -> method.isAnnotationPresent(PostMapping.class));

        assertThat(exposesPost).isFalse();
        assertThat(NotificationController.class.getAnnotation(Profile.class)).isNull();
    }

    @Test
    void auditSearchRemainsRestrictedToAdministrators() {
        PreAuthorize authorization = AdminAuditController.class.getAnnotation(PreAuthorize.class);

        assertThat(authorization).isNotNull();
        assertThat(authorization.value()).isEqualTo("hasRole('ADMIN')");
        assertThat(AdminAuditController.class.getAnnotation(Profile.class)).isNull();
    }
}
