package com.platform.accounts.repository;

import com.platform.accounts.domain.Account;
import com.platform.accounts.domain.AccountStatus;
import com.platform.accounts.domain.AccountType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Uses a real Postgres via Testcontainers, not H2: this test exists specifically
 * to prove optimistic locking behavior, and an in-memory database's locking/typing
 * semantics don't reliably match Postgres's - a pass here on H2 would not actually
 * demonstrate what this test claims to demonstrate.
 * <p>
 * Uses {@link TestEntityManager#clear()} between the two "readers" - without it,
 * both reads would return the same managed instance from one persistence context's
 * identity map, and the conflict this test exists to prove would never actually occur.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class AccountOptimisticLockingIT {

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
    }

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void concurrentUpdatesToTheSameAccountAreRejectedByOptimisticLocking() {
        Account account = new Account();
        account.setUserId(UUID.randomUUID());
        account.setAccountNumber("9999999999");
        account.setAccountType(AccountType.CHECKING);
        account.setBalance(new BigDecimal("100.00"));
        account.setStatus(AccountStatus.ACTIVE);
        Account saved = entityManager.persistFlushFind(account);
        UUID accountId = saved.getId();
        entityManager.clear();

        Account firstReader = accountRepository.findById(accountId).orElseThrow();
        entityManager.clear();
        Account secondReader = accountRepository.findById(accountId).orElseThrow();

        firstReader.setBalance(firstReader.getBalance().subtract(new BigDecimal("30.00")));
        accountRepository.saveAndFlush(firstReader);
        entityManager.clear();

        secondReader.setBalance(secondReader.getBalance().subtract(new BigDecimal("50.00")));

        assertThatThrownBy(() -> accountRepository.saveAndFlush(secondReader))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        entityManager.clear();
        Account reloaded = accountRepository.findById(accountId).orElseThrow();
        assertThat(reloaded.getBalance()).isEqualByComparingTo(new BigDecimal("70.00"));
    }
}