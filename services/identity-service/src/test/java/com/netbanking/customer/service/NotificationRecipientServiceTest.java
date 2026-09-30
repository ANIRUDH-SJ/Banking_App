package com.netbanking.customer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.netbanking.customer.domain.Customer;
import com.netbanking.customer.repository.CustomerRepository;
import com.netbanking.user.domain.AppUser;
import com.netbanking.user.repository.AppUserRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class NotificationRecipientServiceTest {
    @Mock private AppUserRepository users;
    @Mock private CustomerRepository customers;

    @Test
    void returnsTheCustomersEmailAndMobileNumber() {
        AppUser user = new AppUser("asha", "asha@example.com", "password-hash");
        Customer customer =
                new Customer(
                        11L,
                        7L,
                        "CUST000011",
                        "Asha",
                        "Patil",
                        LocalDate.of(1998, 1, 1),
                        "9999999999",
                        "PENDING",
                        "Y");
        when(users.findById(7L)).thenReturn(Optional.of(user));
        when(customers.findByUserId(7L)).thenReturn(Optional.of(customer));

        var recipient = new NotificationRecipientService(users, customers).requireForUser(7L);

        assertThat(recipient.email()).isEqualTo("asha@example.com");
        assertThat(recipient.mobileNumber()).isEqualTo("9999999999");
    }
}
