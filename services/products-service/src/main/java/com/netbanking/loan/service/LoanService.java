package com.netbanking.loan.service;

import com.netbanking.common.exception.ResourceNotFoundException;
import com.netbanking.discovery.CustomerDirectory;
import com.netbanking.loan.api.LoanResponse;
import com.netbanking.loan.domain.Loan;
import com.netbanking.loan.repository.LoanRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class LoanService {

    private final LoanRepository loanRepository;
    private final CustomerDirectory customerService;

    public LoanService(LoanRepository loanRepository, CustomerDirectory customerService) {
        this.loanRepository = loanRepository;
        this.customerService = customerService;
    }

    public List<LoanResponse> getLoans(Long userId) {
        Long customerId = customerService.requireCustomerIdForUser(userId);
        return loanRepository.findByCustomerIdOrderByLoanIdAsc(customerId).stream()
                .map(LoanService::toResponse)
                .toList();
    }

    public LoanResponse getLoan(Long userId, Long loanId) {
        Long customerId = customerService.requireCustomerIdForUser(userId);
        return loanRepository
                .findByLoanIdAndCustomerId(loanId, customerId)
                .map(LoanService::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Loan was not found."));
    }

    public static LoanResponse toResponse(Loan loan) {
        return new LoanResponse(
                loan.getLoanId(),
                loan.getLoanAccountNumber(),
                loan.getLoanType().name(),
                loan.getPrincipalAmount(),
                loan.getOutstandingPrincipal(),
                loan.getInterestRate(),
                loan.getTermMonths(),
                loan.getEmiAmount(),
                loan.getCurrencyCode().trim(),
                loan.getDisbursedOn(),
                loan.getNextDueDate(),
                loan.getMaturityDate(),
                loan.getLoanStatus().name());
    }
}
