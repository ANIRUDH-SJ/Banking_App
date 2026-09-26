package com.netbanking.statement.api;

import com.netbanking.common.api.PagedResponse;
import com.netbanking.security.SecurityContextHelper;
import com.netbanking.statement.service.StatementService;
import com.netbanking.transaction.api.TransactionResponse;
import com.netbanking.transaction.domain.TransactionStatus;
import com.netbanking.transaction.domain.TransactionType;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/accounts/{accountId}")
public class StatementController {

    private final StatementService statementService;

    public StatementController(StatementService statementService) {
        this.statementService = statementService;
    }

    @GetMapping("/statement")
    public PagedResponse<TransactionResponse> list(
            @PathVariable Long accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate to,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PagedResponse.from(
                statementService.getStatement(
                        SecurityContextHelper.currentUserId(),
                        accountId,
                        new StatementFilter(from, to, type, status),
                        page,
                        size));
    }

    @GetMapping(value = "/statement.csv", produces = "text/csv")
    public ResponseEntity<byte[]> export(
            @PathVariable Long accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate to,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) TransactionStatus status) {
        byte[] csv =
                statementService.exportCsv(
                        SecurityContextHelper.currentUserId(),
                        accountId,
                        new StatementFilter(from, to, type, status));
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("statement-" + accountId + ".csv")
                                .build()
                                .toString())
                .body(csv);
    }
}
