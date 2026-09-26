package com.netbanking.loan.api;

import com.netbanking.loan.service.LoanService;
import com.netbanking.security.SecurityContextHelper;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/loans")
public class LoanController {

    private final LoanService loanService;

    public LoanController(LoanService loanService) {
        this.loanService = loanService;
    }

    @GetMapping
    public List<LoanResponse> getLoans() {
        return loanService.getLoans(SecurityContextHelper.currentUserId());
    }

    @GetMapping("/{loanId}")
    public LoanResponse getLoan(@PathVariable Long loanId) {
        return loanService.getLoan(SecurityContextHelper.currentUserId(), loanId);
    }
}
