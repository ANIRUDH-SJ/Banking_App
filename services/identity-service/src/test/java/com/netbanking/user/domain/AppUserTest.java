package com.netbanking.user.domain;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.time.Instant;

class AppUserTest {
    @Test
    void repeatedFailedLoginsLockTheUser() {
        AppUser user = new AppUser("customer", "customer@example.com", "hash");
        Instant lockedUntil = Instant.now().plusSeconds(900);
        user.recordFailedLogin(2, lockedUntil);
        assertEquals(UserStatus.ACTIVE, user.getAccountStatus());
        user.recordFailedLogin(2, lockedUntil);
        assertEquals(UserStatus.LOCKED, user.getAccountStatus());
        assertTrue(user.isLockedAt(Instant.now()));
    }
}
