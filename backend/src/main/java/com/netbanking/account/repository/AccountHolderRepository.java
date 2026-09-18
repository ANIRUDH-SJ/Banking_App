package com.netbanking.account.repository;

import com.netbanking.account.domain.AccountHolder;
import com.netbanking.account.domain.AccountHolderId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountHolderRepository extends JpaRepository<AccountHolder, AccountHolderId> {
    boolean existsByAccountIdAndCustomerIdAndIsActive(Long accountId, Long customerId, String isActive);

    @Query("select holder.accountId from AccountHolder holder "
            + "where holder.customerId = :customerId and holder.isActive = 'Y'")
    List<Long> findActiveAccountIdsByCustomerId(@Param("customerId") Long customerId);
}
