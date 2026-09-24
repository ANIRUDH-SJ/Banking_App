package com.netbanking.beneficiary.service;
import com.netbanking.beneficiary.api.*;
import com.netbanking.beneficiary.domain.Beneficiary;
import com.netbanking.beneficiary.repository.BeneficiaryRepository;
import com.netbanking.common.exception.ConflictException;
import com.netbanking.common.exception.ResourceNotFoundException;
import com.netbanking.customer.service.CustomerService;
import com.netbanking.otp.domain.OtpPurpose;
import com.netbanking.otp.service.OtpService;
import com.netbanking.user.repository.AppUserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional
public class BeneficiaryService {
    private final BeneficiaryRepository repository; private final CustomerService customerService; private final OtpService otpService; private final AppUserRepository userRepository; private final Duration activationCooldown;
    public BeneficiaryService(BeneficiaryRepository repository, CustomerService customerService, OtpService otpService, AppUserRepository userRepository, @org.springframework.beans.factory.annotation.Value("${app.beneficiary.activation-cooldown-minutes:30}") long activationCooldownMinutes) { this.repository = repository; this.customerService = customerService; this.otpService = otpService; this.userRepository = userRepository; this.activationCooldown = Duration.ofMinutes(activationCooldownMinutes); }
    public BeneficiaryResponse create(Long userId, BeneficiaryRequest request) {
        Long customerId = customerService.requireCustomerIdForUser(userId);
        String nickname = request.nickname().trim(); String accountNumber = request.accountNumber().trim(); String ifsc = request.ifscCode().trim().toUpperCase();
        if (repository.existsByCustomerIdAndNicknameIgnoreCase(customerId, nickname) || repository.existsByCustomerIdAndAccountNumberAndIfscCode(customerId, accountNumber, ifsc))
            throw new ConflictException("This beneficiary already exists.");
        return toResponse(repository.save(new Beneficiary(customerId, nickname, request.beneficiaryName().trim(), accountNumber, ifsc, request.bankName().trim())));
    }
    @Transactional(readOnly = true) public List<BeneficiaryResponse> list(Long userId) { return repository.findAllByCustomerIdOrderByNicknameAsc(customerService.requireCustomerIdForUser(userId)).stream().map(this::toResponse).toList(); }
    public BeneficiaryOtpChallengeResponse issueActivationOtp(Long userId, Long beneficiaryId) { Beneficiary b = owned(userId, beneficiaryId); requireCoolingPeriod(b); return new BeneficiaryOtpChallengeResponse(otpService.issue(userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User was not found.")), OtpPurpose.BENEFICIARY_ACTIVATION, activationDigest(userId, b.getBeneficiaryId())).challengeId(), "OTP_SENT"); }
    public BeneficiaryResponse activate(Long userId, Long beneficiaryId, BeneficiaryActivationRequest request) { Beneficiary b = owned(userId, beneficiaryId); requireCoolingPeriod(b); otpService.verifyForUser(userId, request.otpChallengeId(), request.otpCode(), OtpPurpose.BENEFICIARY_ACTIVATION, activationDigest(userId, b.getBeneficiaryId())); b.activate(); return toResponse(b); }
    public void disable(Long userId, Long beneficiaryId) { owned(userId, beneficiaryId).disable(); }
    @Transactional(readOnly = true) public Beneficiary requireActiveOwned(Long userId, Long beneficiaryId) { Beneficiary b = owned(userId, beneficiaryId); if (!b.isActive()) throw new IllegalStateException("Beneficiary is not active."); return b; }
    private Beneficiary owned(Long userId, Long id) { return repository.findByBeneficiaryIdAndCustomerId(id, customerService.requireCustomerIdForUser(userId)).orElseThrow(() -> new ResourceNotFoundException("Beneficiary was not found.")); }
    private void requireCoolingPeriod(Beneficiary b) { if (b.getCreatedAt().plus(activationCooldown).isAfter(Instant.now())) throw new IllegalStateException("Beneficiary activation is available after the cooling period."); }
    private String activationDigest(Long userId, Long beneficiaryId) { try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(("BENEFICIARY_ACTIVATION|" + userId + "|" + beneficiaryId).getBytes(StandardCharsets.UTF_8))); } catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is unavailable.", exception); } }
    private BeneficiaryResponse toResponse(Beneficiary b) { String number = b.getAccountNumber(); return new BeneficiaryResponse(b.getBeneficiaryId(), b.getNickname(), b.getBeneficiaryName(), "****" + number.substring(number.length() - 4), b.getIfscCode(), b.getBankName(), b.getBeneficiaryStatus(), b.getActivatedAt()); }
}
