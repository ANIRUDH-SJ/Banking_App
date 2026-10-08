package com.netbanking.statement.api;

import java.time.LocalDate;

public record StatementEmailRequest(
        LocalDate from,
        LocalDate to,
        StatementTransactionType type,
        StatementTransactionStatus status) {}
