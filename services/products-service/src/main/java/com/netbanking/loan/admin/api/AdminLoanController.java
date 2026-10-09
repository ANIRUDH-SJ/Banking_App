package com.netbanking.loan.admin.api;

import com.netbanking.common.api.PagedResponse;
import com.netbanking.loan.admin.service.AdminLoanService;
import com.netbanking.loan.domain.LoanStatus;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/v1/admin/loans")
public class AdminLoanController {
    private final AdminLoanService loans;

    public AdminLoanController(AdminLoanService loans) {
        this.loans = loans;
    }

    @GetMapping
    public PagedResponse<AdminLoanResponse> search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) LoanStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PagedResponse.from(loans.search(query, customerId, status, page, size));
    }

    @GetMapping("/summary")
    public AdminLoanSummaryResponse summary() {
        return loans.summary();
    }
}
