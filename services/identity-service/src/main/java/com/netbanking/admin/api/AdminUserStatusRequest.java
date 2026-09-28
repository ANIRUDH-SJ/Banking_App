package com.netbanking.admin.api;

import com.netbanking.user.domain.UserStatus;

import jakarta.validation.constraints.NotNull;

public record AdminUserStatusRequest(@NotNull UserStatus status) {}
