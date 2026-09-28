package com.netbanking.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.netbanking.ServiceTestBase;
import com.netbanking.customer.domain.Customer;
import com.netbanking.customer.repository.CustomerRepository;
import com.netbanking.user.domain.AppUser;
import com.netbanking.user.domain.UserStatus;
import com.netbanking.user.repository.AppUserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

class AdminUserSearchIntegrationTest extends ServiceTestBase {
    @Autowired private AdminUserService service;
    @Autowired private AppUserRepository users;
    @Autowired private CustomerRepository customers;

    @Test
    @Transactional
    void filtersUsersAndReturnsTheirCustomerProfile() {
        AppUser user =
                users.saveAndFlush(
                        new AppUser(
                                "adminsearchuser",
                                "admin-search@example.com",
                                "password-hash"));
        customers.saveAndFlush(
                Customer.create(
                        user.getUserId(),
                        "CUSTADMINSEARCH",
                        "Asha",
                        "Patil",
                        LocalDate.of(1998, 1, 1),
                        "9999999999"));

        var result = service.search("ADMIN-SEARCH", UserStatus.ACTIVE, 0, 20);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).username()).isEqualTo("adminsearchuser");
        assertThat(result.getContent().get(0).customer().customerNumber())
                .isEqualTo("CUSTADMINSEARCH");
    }
}
