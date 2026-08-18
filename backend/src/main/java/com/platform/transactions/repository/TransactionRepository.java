package com.platform.transactions.repository;

import com.platform.transactions.domain.Transaction;
import com.platform.transactions.domain.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByIdempotencyKeyAndInitiatedByUserId(String idempotencyKey, UUID initiatedByUserId);

    List<Transaction> findBySourceAccountIdInOrderByCreatedAtDesc(List<UUID> sourceAccountIds);

    List<Transaction> findByStatusOrderByCreatedAtAsc(TransactionStatus status);

    long countByStatus(TransactionStatus status);

    @Query("""
            SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t
            WHERE t.sourceAccountId = :accountId
              AND t.createdAt >= :since
              AND t.status IN :statuses
            """)
    BigDecimal sumAmountFromAccountSince(@Param("accountId") UUID accountId, @Param("since") Instant since,
                                          @Param("statuses") Collection<TransactionStatus> statuses);

    long countBySourceAccountIdAndCreatedAtAfter(UUID sourceAccountId, Instant since);

    List<Transaction> findByInitiatedByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(UUID initiatedByUserId, Instant since);

    long countByStatusAndCreatedAtAfter(TransactionStatus status, Instant since);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.createdAt >= :since")
    BigDecimal sumAmountCreatedSince(@Param("since") Instant since);

    /**
     * Every transaction that has an approval decision recorded - the raw material
     * for the Command Center's average-approval-time and historical SLA-compliance
     * metrics (Feature 4). Computed in Java from these rows rather than pushed
     * into a SQL aggregate, since correctness/readability wins over query
     * efficiency at this data volume - see {@code TransactionMetricsPortImpl}.
     */
    List<Transaction> findByApprovedByUserIdIsNotNull();

    /**
     * The customer's average COMPLETED transfer amount from this account,
     * excluding the transaction currently being sized up against it - used by
     * the Approval Workbench to show an approver "this is Nx their usual
     * amount." Only COMPLETED transfers count, since a FAILED or REJECTED one
     * was never a real, accepted transfer amount for this customer.
     */
    @Query("""
            SELECT AVG(t.amount) FROM Transaction t
            WHERE t.sourceAccountId = :accountId
              AND t.status = :status
              AND t.id <> :excludingTransactionId
            """)
    Optional<BigDecimal> findAverageAmountByStatus(@Param("accountId") UUID accountId,
                                                     @Param("status") TransactionStatus status,
                                                     @Param("excludingTransactionId") UUID excludingTransactionId);

    /**
     * The compare-and-swap primitive TransactionStateMachine builds every transition
     * on top of (architecture doc §16's concurrency table): an explicit precondition
     * on the *current* status in the WHERE clause, so two concurrent attempts at the
     * same transition race safely - one wins, one gets 0 rows affected and a clean
     * "no longer in that state" error, rather than a silent lost update.
     */
    @Modifying
    @Query("UPDATE Transaction t SET t.status = :to WHERE t.id = :id AND t.status = :from")
    int compareAndSetStatus(@Param("id") UUID id, @Param("from") TransactionStatus from, @Param("to") TransactionStatus to);
}
