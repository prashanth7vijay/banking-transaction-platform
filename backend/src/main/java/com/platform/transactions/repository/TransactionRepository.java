package com.platform.transactions.repository;

import com.platform.transactions.domain.Transaction;
import com.platform.transactions.domain.TransactionStatus;
import org.springframework.data.domain.Pageable;
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

    /**
     * Same predicate/ordering as above, but DB-side limited via {@code pageable}
     * (e.g. {@code Pageable.ofSize(20)}) - for Customer 360's recent-activity
     * and timeline sections, which only ever display a fixed, small number of
     * transactions. Avoids pulling a customer's entire 90-day transaction
     * history into the JVM just to keep the first few rows; both queries hit
     * the same {@code (initiated_by_user_id, created_at DESC)} index, so
     * Postgres can satisfy the WHERE, ORDER BY, and LIMIT without a sort step.
     */
    List<Transaction> findByInitiatedByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(
            UUID initiatedByUserId, Instant since, Pageable pageable);

    /**
     * Id-only projection of a customer's transactions in a time window, most
     * recent first. Used to (a) scope the batched risk-assessment and
     * exception lookups without loading full transaction rows, and (b)
     * determine "most recent transaction with an assessment" by walking this
     * already-ordered id list against the batched risk-assessment map -
     * reproducing the old row-order-dependent logic without re-fetching full
     * entities.
     */
    @Query("""
            SELECT t.id FROM Transaction t
            WHERE t.initiatedByUserId = :customerId AND t.createdAt >= :since
            ORDER BY t.createdAt DESC
            """)
    List<UUID> findTransactionIdsForCustomerSince(@Param("customerId") UUID customerId, @Param("since") Instant since);

    /**
     * Customer 360's transaction-activity numbers (counts, sums, 30-day
     * sub-totals, pending/failed counts), computed by Postgres over the full
     * time window in one query instead of being reduced in Java after loading
     * every row of that window. Two time windows are folded into a single scan
     * via CASE-guarded SUMs rather than issuing the query twice. Every SUM is
     * COALESCE-guarded so an empty window (a brand-new customer) yields zeros,
     * not nulls, matching the previous Java-side reduce's starting value of
     * {@code BigDecimal.ZERO}/0.
     */
    @Query("""
            SELECT
                COUNT(t),
                COALESCE(SUM(t.amount), 0),
                COALESCE(SUM(CASE WHEN t.createdAt >= :since30 THEN 1L ELSE 0L END), 0),
                COALESCE(SUM(CASE WHEN t.createdAt >= :since30 THEN t.amount ELSE 0 END), 0),
                COALESCE(SUM(CASE WHEN t.status = :pendingStatus THEN 1L ELSE 0L END), 0),
                COALESCE(SUM(CASE WHEN t.status = :failedStatus THEN 1L ELSE 0L END), 0)
            FROM Transaction t
            WHERE t.initiatedByUserId = :customerId AND t.createdAt >= :since90
            """)
    List<Object[]> aggregateActivityRaw(@Param("customerId") UUID customerId,
                                     @Param("since90") Instant since90,
                                     @Param("since30") Instant since30,
                                     @Param("pendingStatus") TransactionStatus pendingStatus,
                                     @Param("failedStatus") TransactionStatus failedStatus);

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
