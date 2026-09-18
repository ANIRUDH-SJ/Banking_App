package com.netbanking.bank.service;

import com.netbanking.bank.api.BankResponse;
import com.netbanking.bank.domain.Bank;
import com.netbanking.bank.repository.BankRepository;
import com.netbanking.common.exception.ResourceNotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BankService {

    private final BankRepository bankRepository;

    public BankService(BankRepository bankRepository) {
        this.bankRepository = bankRepository;
    }

    public List<BankResponse> getActiveBanks() {
        return bankRepository.findByIsActiveOrderByDisplayNameAsc("Y")
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public BankResponse getBank(Long bankId) {
        return toResponse(findBank(bankId));
    }

    public void requireActiveBank(Long bankId) {
        Bank bank = findBank(bankId);
        if (!"Y".equals(bank.getIsActive())) {
            throw new ResourceNotFoundException("Active bank was not found.");
        }
    }

    private Bank findBank(Long bankId) {
        return bankRepository.findById(bankId)
                .orElseThrow(() -> new ResourceNotFoundException("Bank was not found."));
    }

    private BankResponse toResponse(Bank bank) {
        return new BankResponse(bank.getBankId(), bank.getBankCode(), bank.getLegalName(), bank.getDisplayName());
    }
}
