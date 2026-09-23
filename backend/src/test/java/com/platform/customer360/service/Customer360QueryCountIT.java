package com.platform.customer360.service;

import com.platform.accounts.domain.Account;
import com.platform.accounts.domain.AccountStatus;
import com.platform.accounts.domain.AccountType;
import com.platform.exceptions.domain.ExceptionPriority;
import com.platform.exceptions.domain.ExceptionStatus;
import com.platform.exceptions.domain.TransactionException;
import com.platform.risk.domain.RiskAssessment;
import com.platform.risk.domain.RiskLevel;
import com.platform.transactions.domain.Transaction;
import com.platform.transactions.domain.TransactionStatus;
import com.platform.transactions.domain.TransactionType;
import com.platform.users.domain.User;
import com.platform.users.domain.UserStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Session;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class Customer360QueryCountIT {

    private static final int TRANSACTIONS_PER_CUSTOMER = 50;
    private static final int MAX_ALLOWED_QUERIES = 10;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("platform_test")
            .withUsername("platform")
            .withPassword("platform");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // Needed to read Session.getSessionFactory().getStatistics() below.
        registry.add("spring.jpa.properties.hibernate.generate_statistics", () -> "true");
    }

    @Autowired
    private Customer360Service customer360Service;

    @PersistenceContext
    private EntityManager entityManager;

    private UUID customerId;

    @BeforeEach
    @Transactional
    void seedCustomerWithActivity() {
        User customer = new User();
        customer.setEmail("bench-" + UUID.randomUUID() + "@example.com");
        customer.setPasswordHash("n/a");
        customer.setFirstName("Bench");
        customer.setLastName("Customer");
        customer.setStatus(UserStatus.ACTIVE);
        entityManager.persist(customer);
        customerId = customer.getId();

        Account account = new Account();
        account.setUserId(customerId);
        account.setAccountNumber("ACC-" + UUID.randomUUID().toString().substring(0, 8));
        account.setAccountType(AccountType.CHECKING);
        account.setBalance(new BigDecimal("10000.00"));
        account.setStatus(AccountStatus.ACTIVE);
        entityManager.persist(account);

        User assignee = new User();
        assignee.setEmail("assignee-" + UUID.randomUUID() + "@example.com");
        assignee.setPasswordHash("n/a");
        assignee.setFirstName("Investigator");
        assignee.setLastName("One");
        assignee.setStatus(UserStatus.ACTIVE);
        entityManager.persist(assignee);

        for (int i = 0; i < TRANSACTIONS_PER_CUSTOMER; i++) {
            Transaction tx = new Transaction();
            tx.setSourceAccountId(account.getId());
            tx.setDestinationAccountNumber("DEST-0000000" + (i % 9));
            tx.setAmount(BigDecimal.valueOf(50 + i));
            tx.setCurrency("USD");
            tx.setStatus(i % 10 == 0 ? TransactionStatus.FAILED : TransactionStatus.COMPLETED);
            tx.setType(TransactionType.TRANSFER);
            tx.setIdempotencyKey("bench-" + UUID.randomUUID());
            tx.setInitiatedByUserId(customerId);
            entityManager.persist(tx);
            // Every transaction here is well within the 90-day window this test cares about;
            // Transaction.createdAt is a @CreationTimestamp, so it's set on flush/persist.

            // Every third transaction has a risk assessment - enough to exercise the
            // batched risk lookup without every transaction needing one.
            if (i % 3 == 0) {
                RiskAssessment risk = new RiskAssessment();
                risk.setTransactionId(tx.getId());
                risk.setRiskLevel(i % 9 == 0 ? RiskLevel.HIGH : RiskLevel.LOW);
                risk.setReasons("[\"VELOCITY_LIMIT\"]");
                risk.setBlocked(false);
                entityManager.persist(risk);
            }

            // Every failed transaction gets an open exception with an assignee -
            // exercises both the batched exception lookup and the batched
            // assignee-name lookup.
            if (tx.getStatus() == TransactionStatus.FAILED) {
                TransactionException exception = new TransactionException();
                exception.setTransactionId(tx.getId());
                exception.setStatus(ExceptionStatus.OPEN);
                exception.setPriority(ExceptionPriority.HIGH);
                exception.setReason("Posting failed");
                exception.setAssignedToUserId(assignee.getId());
                exception.setSlaDueAt(Instant.now().plus(1, ChronoUnit.DAYS));
                entityManager.persist(exception);
            }
        }
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void customer360IssuesAFixedNumberOfQueriesRegardlessOfTransactionCount() {
        Statistics statistics = entityManager.unwrap(Session.class).getSessionFactory().getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        customer360Service.getSummary(customerId);

        long queryCount = statistics.getQueryExecutionCount();
        assertThat(queryCount)
                .as("Customer 360 should issue a small, constant number of queries " +
                        "(batched lookups), not one per transaction/exception. " +
                        "%d transactions were seeded; a reintroduced N+1 would blow well past %d queries.",
                        TRANSACTIONS_PER_CUSTOMER, MAX_ALLOWED_QUERIES)
                .isLessThanOrEqualTo(MAX_ALLOWED_QUERIES);
    }
}