package com.netbanking.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.netbanking.user.domain.AppUser;
import com.netbanking.user.repository.AppUserRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private AppUserRepository userRepository;

    @Test
    void persistsFailedLoginStateInItsOwnTransaction() {
        AppUser user = new AppUser("asha", "asha@example.com", "password-hash");
        UserService service = new UserService(userRepository, 1, 15);

        service.recordFailedLogin(user);

        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
        assertThat(user.isLockedAt(Instant.now())).isTrue();
        verify(userRepository).save(user);
    }
}
