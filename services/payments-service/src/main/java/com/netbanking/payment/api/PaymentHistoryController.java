package com.netbanking.payment.api;

import com.netbanking.payment.service.PaymentWorkflowStore;
import com.netbanking.security.SecurityContextHelper;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class PaymentHistoryController {
    private final PaymentWorkflowStore store;

    public PaymentHistoryController(PaymentWorkflowStore store) {
        this.store = store;
    }

    @GetMapping("/transfers")
    public List<PaymentReceiptResponse> transfers() {
        return store.history(SecurityContextHelper.currentUserId(), "TRANSFER");
    }

    @GetMapping("/bill-payments")
    public List<PaymentReceiptResponse> bills() {
        return store.history(SecurityContextHelper.currentUserId(), "BILL_PAYMENT");
    }
}
