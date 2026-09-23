package com.netbanking.card.api;

import com.netbanking.card.domain.CardStatusAction;
import jakarta.validation.constraints.NotNull;

public record ChangeCardStatusRequest(@NotNull CardStatusAction action) {
}
