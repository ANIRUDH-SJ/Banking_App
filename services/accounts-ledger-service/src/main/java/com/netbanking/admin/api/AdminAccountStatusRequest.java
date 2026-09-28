package com.netbanking.admin.api;

import com.netbanking.account.api.AccountStatus;

import jakarta.validation.constraints.NotNull;

public record AdminAccountStatusRequest(@NotNull AccountStatus status) {}
