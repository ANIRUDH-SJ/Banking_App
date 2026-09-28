package com.netbanking.user.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netbanking.audit.IdentityAuditService;
import com.netbanking.common.exception.ConflictException;
import com.netbanking.customer.domain.Customer;
import com.netbanking.customer.repository.CustomerRepository;
import com.netbanking.user.domain.AppUser;
import com.netbanking.user.domain.UserStatus;
import com.netbanking.user.repository.AppUserRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {
    @Mock private AppUserRepository users;
    @Mock private CustomerRepository customers;
    @Mock private IdentityAuditService audit;

    @Test
    void searchReturnsCustomerContextWithoutOneQueryPerUser() {
        AppUser user = user(7L);
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
        when(users.findAll(
                        org.mockito.ArgumentMatchers.<Specification<AppUser>>any(),
                        any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(user)));
        when(customers.findByUserIdIn(List.of(7L))).thenReturn(List.of(customer));

        var result = service().search("asha", UserStatus.ACTIVE, 0, 20);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).customer().customerNumber())
                .isEqualTo("CUST000011");
        verify(customers).findByUserIdIn(List.of(7L));
    }

    @Test
    void administratorCanDisableAnotherUserAndTheActionIsAudited() {
        AppUser user = user(7L);
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        when(customers.findByUserId(7L)).thenReturn(Optional.empty());

        var result = service().changeStatus(99L, 7L, UserStatus.DISABLED);

        assertThat(result.status()).isEqualTo("DISABLED");
        verify(audit)
                .success(
                        99L,
                        "ADMIN_USER_STATUS_CHANGED",
                        "USER",
                        "7",
                        "from=ACTIVE;to=DISABLED");
    }

    @Test
    void administratorCannotDisableOwnUser() {
        assertThatThrownBy(() -> service().changeStatus(7L, 7L, UserStatus.DISABLED))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("own status");
    }

    private AdminUserService service() {
        return new AdminUserService(users, customers, audit);
    }

    private static AppUser user(Long userId) {
        AppUser user = new AppUser("asha", "asha@example.com", "password-hash");
        ReflectionTestUtils.setField(user, "userId", userId);
        return user;
    }
}
