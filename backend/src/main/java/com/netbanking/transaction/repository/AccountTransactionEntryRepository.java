package com.netbanking.transaction.repository;

import com.netbanking.transaction.domain.AccountTransactionEntry;
import com.netbanking.transaction.domain.EntryType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;

public interface AccountTransactionEntryRepository extends JpaRepository<AccountTransactionEntry, Long>,
        JpaSpecificationExecutor<AccountTransactionEntry> {

    @EntityGraph(attributePaths = "transaction")
    Page<AccountTransactionEntry> findByAccountId(Long accountId, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "transaction")
    Page<AccountTransactionEntry> findAll(Specification<AccountTransactionEntry> specification, Pageable pageable);

    @EntityGraph(attributePaths = "transaction")
    Optional<AccountTransactionEntry> findByEntryIdAndAccountId(Long entryId, Long accountId);

    boolean existsByTransactionTransactionIdAndAccountIdAndEntryType(
            Long transactionId, Long accountId, EntryType entryType);
}
