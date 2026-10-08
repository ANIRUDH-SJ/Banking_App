package com.netbanking.account.api;

import jakarta.validation.constraints.Size;

/** An empty or missing nickname clears it. */
public record RenameAccountRequest(@Size(max = 40) String nickname) {}
