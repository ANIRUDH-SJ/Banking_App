package com.netbanking.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netbanking.user.domain.AppUser;
import com.netbanking.user.repository.AppUserRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private AppUserRepository userRepository;

    @Test
    void persistsFailedLoginStateInItsOwnTransaction() {
        AppUser user = new AppUser("asha", "asha@example.com", "password-hash");
        ReflectionTestUtils.setField(user, "userId", 7L);
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        UserService service = new UserService(userRepository, 1, 15);

        service.recordFailedLogin(7L);

        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
        assertThat(user.isLockedAt(Instant.now())).isTrue();
        verify(userRepository).findByIdForUpdate(7L);
    }
}
