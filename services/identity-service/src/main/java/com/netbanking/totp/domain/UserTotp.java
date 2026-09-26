package com.netbanking.totp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "user_totp")
public class UserTotp {
    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "secret_ciphertext", nullable = false)
    private String secretCiphertext;

    @Column(name = "is_enabled", nullable = false, columnDefinition = "CHAR(1)")
    private String isEnabled;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "last_used_time_step")
    private Long lastUsedTimeStep;

    protected UserTotp() {}

    public UserTotp(Long userId, String secretCiphertext) {
        this.userId = userId;
        this.secretCiphertext = secretCiphertext;
        this.isEnabled = "N";
    }

    public Long getUserId() {
        return userId;
    }

    public String getSecretCiphertext() {
        return secretCiphertext;
    }

    public boolean isEnabled() {
        return "Y".equals(isEnabled);
    }

    public void replaceSecret(String secretCiphertext) {
        this.secretCiphertext = secretCiphertext;
        this.isEnabled = "N";
        this.confirmedAt = null;
        this.lastUsedTimeStep = null;
    }

    public void confirm() {
        this.isEnabled = "Y";
        this.confirmedAt = Instant.now();
    }

    public boolean acceptTimeStep(long timeStep) {
        if (lastUsedTimeStep != null && timeStep <= lastUsedTimeStep) return false;
        lastUsedTimeStep = timeStep;
        return true;
    }
}
