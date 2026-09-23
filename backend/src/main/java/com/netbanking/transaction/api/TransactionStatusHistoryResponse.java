package com.netbanking.transaction.api;

import java.time.LocalDateTime;

public record TransactionStatusHistoryResponse(
        Long historyId,
        String previousStatus,
        String newStatus,
        String failureReason,
        Long changedByUserId,
        LocalDateTime changedAt) {
}
