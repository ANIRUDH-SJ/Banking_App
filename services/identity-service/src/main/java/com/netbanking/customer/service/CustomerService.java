package com.netbanking.customer.service;

import com.netbanking.audit.IdentityAuditService;
import com.netbanking.common.exception.ResourceNotFoundException;
import com.netbanking.customer.api.CustomerProfileResponse;
import com.netbanking.customer.api.CustomerProfileUpdateRequest;
import com.netbanking.customer.domain.Customer;
import com.netbanking.customer.repository.CustomerRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final IdentityAuditService audit;

    public CustomerService(CustomerRepository customerRepository, IdentityAuditService audit) {
        this.customerRepository = customerRepository;
        this.audit = audit;
    }

    public CustomerProfileResponse getProfileForUser(Long userId) {
        return toResponse(findCustomerByUserId(userId));
    }

    public Long requireCustomerIdForUser(Long userId) {
        return findCustomerByUserId(userId).getCustomerId();
    }

    @Transactional
    public CustomerProfileResponse updateProfileForUser(
            Long userId, CustomerProfileUpdateRequest request) {
        Customer customer = findCustomerByUserId(userId);
        customer.updateProfile(
                request.firstName().trim(),
                request.lastName().trim(),
                request.mobileNumber().replace(" ", "").replace("-", ""));
        Customer saved = customerRepository.saveAndFlush(customer);
        audit.success(
                userId,
                "CUSTOMER_PROFILE_UPDATED",
                "CUSTOMER",
                String.valueOf(saved.getCustomerId()));
        return toResponse(saved);
    }

    private Customer findCustomerByUserId(Long userId) {
        return customerRepository
                .findByUserId(userId)
                .orElseThrow(
                        () -> new ResourceNotFoundException("Customer profile was not found."));
    }

    private CustomerProfileResponse toResponse(Customer customer) {
        return new CustomerProfileResponse(
                customer.getCustomerId(),
                customer.getCustomerNumber(),
                customer.getFirstName(),
                customer.getLastName(),
                customer.getDateOfBirth(),
                customer.getMobileNumber(),
                customer.getKycStatus(),
                "Y".equals(customer.getIsActive()));
    }
}
