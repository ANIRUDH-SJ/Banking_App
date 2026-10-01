package com.netbanking.payment.api;

import com.netbanking.common.api.PagedResponse;
import com.netbanking.payment.service.PaymentSearchService;
import com.netbanking.payment.service.PaymentWorkflowStore;
import com.netbanking.security.SecurityContextHelper;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class PaymentHistoryController {
    private final PaymentWorkflowStore store;
    private final PaymentSearchService search;

    public PaymentHistoryController(PaymentWorkflowStore store, PaymentSearchService search) {
        this.store = store;
        this.search = search;
    }

    @GetMapping("/transfers")
    public List<PaymentReceiptResponse> transfers() {
        return store.history(SecurityContextHelper.currentUserId(), "TRANSFER");
    }

    @GetMapping("/bill-payments")
    public List<PaymentReceiptResponse> bills() {
        return store.history(SecurityContextHelper.currentUserId(), "BILL_PAYMENT");
    }

    @GetMapping("/payments")
    public PagedResponse<PaymentHistoryResponse> search(
            @RequestParam(required = false) PaymentKind kind,
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate to,
            @RequestParam(required = false) String transactionReference,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PagedResponse.from(
                search.search(
                        SecurityContextHelper.currentUserId(),
                        new PaymentSearchFilter(kind, status, from, to, transactionReference),
                        page,
                        size));
    }

    @GetMapping("/payments/{paymentId}")
    public PaymentHistoryResponse get(@PathVariable Long paymentId) {
        return search.get(SecurityContextHelper.currentUserId(), paymentId);
    }
}
