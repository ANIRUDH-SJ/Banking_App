package com.netbanking.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.netbanking.ServiceTestBase;
import com.netbanking.admin.api.AuditOutcome;
import com.netbanking.admin.api.AuditSearchFilter;
import com.netbanking.audit.domain.AuditEvent;
import com.netbanking.audit.repository.AuditEventRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AdminServiceTest extends ServiceTestBase {
    @Autowired private AdminService service;
    @Autowired private AuditEventRepository repository;

    @BeforeEach
    void clearEvents() {
        repository.deleteAll();
    }

    @Test
    void filtersAuditEventsWithoutReturningOtherUsersRecords() {
        repository.saveAndFlush(
                new AuditEvent(
                        7L,
                        "CARD_BLOCK",
                        "CARD",
                        "3",
                        "SUCCESS",
                        "source=test"));
        repository.saveAndFlush(
                new AuditEvent(8L, "LOGIN_REJECTED", "USER", "8", "DENIED"));

        var result =
                service.search(
                        new AuditSearchFilter(
                                7L, "card_block", AuditOutcome.SUCCESS, null, null),
                        0,
                        20);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getEntityId()).isEqualTo("3");
        assertThat(result.getContent().get(0).getEventDetails()).isEqualTo("source=test");
    }
}
