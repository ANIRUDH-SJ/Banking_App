package com.netbanking.branch.service;

import com.netbanking.bank.service.BankService;
import com.netbanking.branch.api.BranchResponse;
import com.netbanking.branch.domain.Branch;
import com.netbanking.branch.repository.BranchRepository;
import com.netbanking.common.exception.ResourceNotFoundException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class BranchService {

    private final BranchRepository branchRepository;
    private final BankService bankService;

    public BranchService(BranchRepository branchRepository, BankService bankService) {
        this.branchRepository = branchRepository;
        this.bankService = bankService;
    }

    public List<BranchResponse> getActiveBranches(Long bankId) {
        bankService.requireActiveBank(bankId);
        return branchRepository.findByBankIdAndIsActiveOrderByBranchNameAsc(bankId, "Y").stream()
                .map(this::toResponse)
                .toList();
    }

    public BranchResponse getBranch(Long branchId) {
        return toResponse(findBranch(branchId));
    }

    private Branch findBranch(Long branchId) {
        return branchRepository
                .findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch was not found."));
    }

    private BranchResponse toResponse(Branch branch) {
        return new BranchResponse(
                branch.getBranchId(),
                branch.getBankId(),
                branch.getBranchCode(),
                branch.getBranchName(),
                branch.getIfscCode(),
                branch.getCity(),
                branch.getState());
    }
}
