package com.netbanking.transaction.repository;

import com.netbanking.transaction.domain.AccountTransactionEntry;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountTransactionEntryRepository extends JpaRepository<AccountTransactionEntry, Long> {

    @EntityGraph(attributePaths = "transaction")
    Page<AccountTransactionEntry> findByAccountId(Long accountId, Pageable pageable);

    @EntityGraph(attributePaths = "transaction")
    Optional<AccountTransactionEntry> findByEntryIdAndAccountId(Long entryId, Long accountId);
}
