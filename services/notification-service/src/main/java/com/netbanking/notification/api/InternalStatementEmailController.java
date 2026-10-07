package com.netbanking.notification.api;

import com.netbanking.notification.service.StatementEmailService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasAuthority('SERVICE_accounts-ledger-service')")
public class InternalStatementEmailController {
    private final StatementEmailService statements;

    public InternalStatementEmailController(StatementEmailService statements) {
        this.statements = statements;
    }

    @PostMapping("/internal/statement-emails")
    public StatementEmailService.Receipt send(@Valid @RequestBody Request request) {
        return statements.send(
                new StatementEmailService.Command(
                        request.userId(),
                        request.subject(),
                        request.body(),
                        request.attachmentName(),
                        request.attachmentBase64()));
    }

    public record Request(
            @NotNull @Positive Long userId,
            @NotBlank @Size(max = 150) String subject,
            @NotBlank @Size(max = 2000) String body,
            @NotBlank @Size(max = 80) String attachmentName,
            @NotBlank @Size(max = 7_000_000) String attachmentBase64) {}
}
