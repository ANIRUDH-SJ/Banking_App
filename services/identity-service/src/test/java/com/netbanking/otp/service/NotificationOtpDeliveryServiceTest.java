package com.netbanking.otp.service;

import static org.mockito.Mockito.*;

import com.netbanking.customer.domain.Customer;
import com.netbanking.customer.repository.CustomerRepository;
import com.netbanking.discovery.OtpDeliveryClient;
import com.netbanking.otp.domain.OtpPurpose;
import com.netbanking.user.domain.AppUser;
import com.netbanking.user.repository.AppUserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

class NotificationOtpDeliveryServiceTest {
    @Test
    void sendsTheCodeToRegisteredEmailAndMobileWithoutPersistingIt() {
        var users = mock(AppUserRepository.class);
        var customers = mock(CustomerRepository.class);
        var client = mock(OtpDeliveryClient.class);
        var user = new AppUser("asha", "asha@example.com", "hash");
        ReflectionTestUtils.setField(user, "userId", 7L);
        var customer =
                new Customer(
                        11L,
                        7L,
                        "CUST000007",
                        "Asha",
                        "Rao",
                        LocalDate.of(1990, 1, 1),
                        "+919876543210",
                        "VERIFIED",
                        "Y");
        when(users.findById(7L)).thenReturn(Optional.of(user));
        when(customers.findByUserId(7L)).thenReturn(Optional.of(customer));
        Instant expiry = Instant.parse("2026-09-29T10:30:00Z");

        new NotificationOtpDeliveryService(users, customers, client)
                .deliver(7L, "challenge-1", OtpPurpose.LOGIN, "123456", expiry);

        verify(client)
                .deliver(
                        new OtpDeliveryClient.Command(
                                "challenge-1",
                                7L,
                                "asha@example.com",
                                "+919876543210",
                                "LOGIN",
                                "123456",
                                expiry));
    }

    @Test
    void fallsBackToEmailBeforeCustomerOnboardingCompletes() {
        var users = mock(AppUserRepository.class);
        var customers = mock(CustomerRepository.class);
        var client = mock(OtpDeliveryClient.class);
        var user = new AppUser("asha", "asha@example.com", "hash");
        ReflectionTestUtils.setField(user, "userId", 7L);
        when(users.findById(7L)).thenReturn(Optional.of(user));
        when(customers.findByUserId(7L)).thenReturn(Optional.empty());
        Instant expiry = Instant.parse("2026-09-29T10:30:00Z");

        new NotificationOtpDeliveryService(users, customers, client)
                .deliver(7L, "challenge-2", OtpPurpose.LOGIN, "654321", expiry);

        verify(client)
                .deliver(
                        new OtpDeliveryClient.Command(
                                "challenge-2",
                                7L,
                                "asha@example.com",
                                null,
                                "LOGIN",
                                "654321",
                                expiry));
    }
}
