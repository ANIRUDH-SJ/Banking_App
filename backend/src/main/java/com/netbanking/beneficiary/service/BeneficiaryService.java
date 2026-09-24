package com.netbanking.beneficiary.service;
import com.netbanking.beneficiary.api.*;
import com.netbanking.beneficiary.domain.Beneficiary;
import com.netbanking.beneficiary.repository.BeneficiaryRepository;
import com.netbanking.common.exception.ConflictException;
import com.netbanking.common.exception.ResourceNotFoundException;
import com.netbanking.customer.service.CustomerService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional
public class BeneficiaryService {
    private final BeneficiaryRepository repository; private final CustomerService customerService;
    public BeneficiaryService(BeneficiaryRepository repository, CustomerService customerService) { this.repository = repository; this.customerService = customerService; }
    public BeneficiaryResponse create(Long userId, BeneficiaryRequest request) {
        Long customerId = customerService.requireCustomerIdForUser(userId);
        String nickname = request.nickname().trim(); String accountNumber = request.accountNumber().trim(); String ifsc = request.ifscCode().trim().toUpperCase();
        if (repository.existsByCustomerIdAndNicknameIgnoreCase(customerId, nickname) || repository.existsByCustomerIdAndAccountNumberAndIfscCode(customerId, accountNumber, ifsc))
            throw new ConflictException("This beneficiary already exists.");
        return toResponse(repository.save(new Beneficiary(customerId, nickname, request.beneficiaryName().trim(), accountNumber, ifsc, request.bankName().trim())));
    }
    @Transactional(readOnly = true) public List<BeneficiaryResponse> list(Long userId) { return repository.findAllByCustomerIdOrderByNicknameAsc(customerService.requireCustomerIdForUser(userId)).stream().map(this::toResponse).toList(); }
    public BeneficiaryResponse activate(Long userId, Long beneficiaryId) { Beneficiary b = owned(userId, beneficiaryId); b.activate(); return toResponse(b); }
    public void disable(Long userId, Long beneficiaryId) { owned(userId, beneficiaryId).disable(); }
    @Transactional(readOnly = true) public Beneficiary requireActiveOwned(Long userId, Long beneficiaryId) { Beneficiary b = owned(userId, beneficiaryId); if (!b.isActive()) throw new IllegalStateException("Beneficiary is not active."); return b; }
    private Beneficiary owned(Long userId, Long id) { return repository.findByBeneficiaryIdAndCustomerId(id, customerService.requireCustomerIdForUser(userId)).orElseThrow(() -> new ResourceNotFoundException("Beneficiary was not found.")); }
    private BeneficiaryResponse toResponse(Beneficiary b) { String number = b.getAccountNumber(); return new BeneficiaryResponse(b.getBeneficiaryId(), b.getNickname(), b.getBeneficiaryName(), "****" + number.substring(number.length() - 4), b.getIfscCode(), b.getBankName(), b.getBeneficiaryStatus(), b.getActivatedAt()); }
}
