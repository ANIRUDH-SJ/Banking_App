package com.netbanking.loginaudit.service;

import com.netbanking.loginaudit.domain.LoginAudit;
import com.netbanking.loginaudit.repository.LoginAuditRepository;
import com.netbanking.user.domain.AppUser;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class LoginAuditService {
    private final LoginAuditRepository repository;
    private final Clock clock;

    @Autowired
    public LoginAuditService(LoginAuditRepository repository) {
        this(repository, Clock.systemUTC());
    }

    LoginAuditService(LoginAuditRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void success(AppUser user, LoginAttemptContext context) {
        repository.save(
                LoginAudit.success(
                        user, context.clientIpAddress(), context.userAgent(), Instant.now(clock)));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failure(
            AppUser user,
            String usernameAttempted,
            String reason,
            LoginAttemptContext context) {
        repository.save(
                LoginAudit.failure(
                        user,
                        usernameAttempted,
                        reason,
                        context.clientIpAddress(),
                        context.userAgent(),
                        Instant.now(clock)));
    }
}
