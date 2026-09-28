package com.netbanking.customer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netbanking.audit.IdentityAuditService;
import com.netbanking.common.exception.ResourceNotFoundException;
import com.netbanking.customer.api.CustomerProfileUpdateRequest;
import com.netbanking.customer.domain.Customer;
import com.netbanking.customer.repository.CustomerRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock private CustomerRepository customerRepository;
    @Mock private IdentityAuditService audit;

    @Test
    void returnsTheProfileForTheAuthenticatedUser() {
        Customer customer =
                new Customer(
                        10L,
                        99L,
                        "CUST000010",
                        "Asha",
                        "Patil",
                        LocalDate.of(1998, 1, 1),
                        "9999999999",
                        "VERIFIED",
                        "Y");
        when(customerRepository.findByUserId(99L)).thenReturn(Optional.of(customer));

        CustomerService service = new CustomerService(customerRepository, audit);

        assertThat(service.getProfileForUser(99L).customerNumber()).isEqualTo("CUST000010");
        assertThat(service.requireCustomerIdForUser(99L)).isEqualTo(10L);
    }

    @Test
    void rejectsAUserWithoutACustomerProfile() {
        when(customerRepository.findByUserId(99L)).thenReturn(Optional.empty());

        CustomerService service = new CustomerService(customerRepository, audit);

        assertThatThrownBy(() -> service.getProfileForUser(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updatesOnlyTheEditableProfileFields() {
        Customer customer =
                new Customer(
                        10L,
                        99L,
                        "CUST000010",
                        "Asha",
                        "Patil",
                        LocalDate.of(1998, 1, 1),
                        "9999999999",
                        "VERIFIED",
                        "Y");
        when(customerRepository.findByUserId(99L)).thenReturn(Optional.of(customer));
        when(customerRepository.saveAndFlush(customer)).thenReturn(customer);

        CustomerService service = new CustomerService(customerRepository, audit);
        var response =
                service.updateProfileForUser(
                        99L, new CustomerProfileUpdateRequest("Asha ", " Rao ", "+91 98765-43210"));

        assertThat(response.firstName()).isEqualTo("Asha");
        assertThat(response.lastName()).isEqualTo("Rao");
        assertThat(response.mobileNumber()).isEqualTo("+919876543210");
        assertThat(response.dateOfBirth()).isEqualTo(LocalDate.of(1998, 1, 1));
        verify(customerRepository).saveAndFlush(customer);
        verify(audit).success(99L, "CUSTOMER_PROFILE_UPDATED", "CUSTOMER", "10");
    }
}
