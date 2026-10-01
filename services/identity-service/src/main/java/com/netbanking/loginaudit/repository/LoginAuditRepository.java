package com.netbanking.loginaudit.repository;

import com.netbanking.loginaudit.domain.LoginAudit;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginAuditRepository extends JpaRepository<LoginAudit, Long> {}
