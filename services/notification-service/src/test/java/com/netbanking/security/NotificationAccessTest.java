package com.netbanking.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.netbanking.notification.api.NotificationController;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.Arrays;

class NotificationAccessTest {
    @Test
    void notificationApiDoesNotExposePublicCreationAndIsNotProfileGated() {
        boolean exposesPost =
                Arrays.stream(NotificationController.class.getDeclaredMethods())
                        .anyMatch(method -> method.isAnnotationPresent(PostMapping.class));

        assertThat(exposesPost).isFalse();
        assertThat(NotificationController.class.getAnnotation(Profile.class)).isNull();
    }
}
