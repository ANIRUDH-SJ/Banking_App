package com.netbanking.customer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.netbanking.common.exception.ResourceNotFoundException;
import com.netbanking.customer.domain.Customer;
import com.netbanking.customer.repository.CustomerRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Test
    void returnsTheProfileForTheAuthenticatedUser() {
        Customer customer = new Customer(10L, 99L, "CUST000010", "Asha", "Patil",
                LocalDate.of(1998, 1, 1), "9999999999", "VERIFIED", "Y");
        when(customerRepository.findByUserId(99L)).thenReturn(Optional.of(customer));

        CustomerService service = new CustomerService(customerRepository);

        assertThat(service.getProfileForUser(99L).customerNumber()).isEqualTo("CUST000010");
        assertThat(service.requireCustomerIdForUser(99L)).isEqualTo(10L);
    }

    @Test
    void rejectsAUserWithoutACustomerProfile() {
        when(customerRepository.findByUserId(99L)).thenReturn(Optional.empty());

        CustomerService service = new CustomerService(customerRepository);

        assertThatThrownBy(() -> service.getProfileForUser(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
