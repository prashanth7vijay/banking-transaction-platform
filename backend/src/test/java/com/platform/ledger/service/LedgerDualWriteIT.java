package com.platform.ledger.service;

import com.platform.accounts.domain.AccountType;
import com.platform.accounts.port.AccountLookupPort;
import com.platform.accounts.service.AccountService;
import com.platform.auth.service.TokenDenylistService;
import com.platform.ledger.repository.LedgerAccountRepository;
import com.platform.transactions.domain.Transaction;
import com.platform.transactions.service.ApprovalService;
import com.platform.transactions.service.TransferService;
import com.platform.users.domain.User;
import com.platform.users.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real Postgres, not H2 - this test exists to prove the atomic-commit guarantee
 * between accounts.accounts.balance and ledger.ledger_accounts.ledger_balance
 * (Phase 2 of the enterprise-evolution plan), and real Flyway migrations plus
 * real transactional/locking behavior are exactly what that guarantee depends on.
 * <p>
 * TokenDenylistService is mocked purely to avoid needing a live Redis for a test
 * that never calls login/refresh/logout - same pattern RbacMatrixIT already uses.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class LedgerDualWriteIT {

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
    private UserService userService;
    @Autowired
    private AccountService accountService;
    @Autowired
    private TransferService transferService;
    @Autowired
    private ApprovalService approvalService;
    @Autowired
    private AccountLookupPort accountLookupPort;
    @Autowired
    private LedgerAccountRepository ledgerAccountRepository;

    @MockBean
    private TokenDenylistService tokenDenylistService;

    @Test
    void approvedTransferKeepsLedgerAndMaterializedBalanceInAgreement() {
        UUID employeeId = UUID.randomUUID();

        User customer = userService.createCustomer(
                "ledger-it-" + UUID.randomUUID() + "@platform.local", "Password123", "Ledger", "Tester");

        var checking = accountService.openAccountForCustomer(
                employeeId, customer.getId(), AccountType.CHECKING, new BigDecimal("1000.00"));
        var savings = accountService.openAccountForCustomer(
                employeeId, customer.getId(), AccountType.SAVINGS, new BigDecimal("0.00"));

        Transaction transaction = transferService.createTransfer(
                customer.getId(), checking.getId(), savings.getAccountNumber(),
                new BigDecimal("250.00"), UUID.randomUUID().toString(), "Ledger IT test transfer");

        approvalService.approve(employeeId, transaction.getId());

        var checkingSummary = accountLookupPort.getById(checking.getId());
        var savingsSummary = accountLookupPort.getById(savings.getId());
        var checkingLedger = ledgerAccountRepository.findByAccountId(checking.getId()).orElseThrow();
        var savingsLedger = ledgerAccountRepository.findByAccountId(savings.getId()).orElseThrow();

        assertThat(checkingSummary.balance()).isEqualByComparingTo("750.00");
        assertThat(savingsSummary.balance()).isEqualByComparingTo("250.00");
        assertThat(checkingLedger.getLedgerBalance()).isEqualByComparingTo(checkingSummary.balance());
        assertThat(savingsLedger.getLedgerBalance()).isEqualByComparingTo(savingsSummary.balance());
    }
}
