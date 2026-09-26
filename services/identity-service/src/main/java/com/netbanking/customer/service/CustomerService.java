package com.netbanking.customer.service;

import com.netbanking.common.exception.ResourceNotFoundException;
import com.netbanking.customer.api.CustomerProfileResponse;
import com.netbanking.customer.domain.Customer;
import com.netbanking.customer.repository.CustomerRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public CustomerProfileResponse getProfileForUser(Long userId) {
        return toResponse(findCustomerByUserId(userId));
    }

    public Long requireCustomerIdForUser(Long userId) {
        return findCustomerByUserId(userId).getCustomerId();
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
                customer.getMobileNumber(),
                customer.getKycStatus(),
                "Y".equals(customer.getIsActive()));
    }
}
