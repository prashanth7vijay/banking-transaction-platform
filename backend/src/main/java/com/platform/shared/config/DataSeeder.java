package com.platform.shared.config;

import com.platform.accounts.domain.Account;
import com.platform.accounts.domain.AccountStatus;
import com.platform.accounts.domain.AccountType;
import com.platform.accounts.repository.AccountRepository;
import com.platform.risk.port.RiskAssessmentPort;
import com.platform.risk.service.RiskAssessmentResult;
import com.platform.transactions.domain.Transaction;
import com.platform.transactions.domain.TransactionStatus;
import com.platform.transactions.repository.TransactionRepository;
import com.platform.transactions.service.TransactionStateMachine;
import com.platform.transactions.service.TransferService;
import com.platform.users.domain.Role;
import com.platform.users.domain.RoleName;
import com.platform.users.domain.User;
import com.platform.users.domain.UserStatus;
import com.platform.users.repository.RoleRepository;
import com.platform.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

/**
 * Seeds demo accounts (and a sample pending transfer) in the `dev` profile only, so
 * `docker compose up` is demo-ready with zero manual steps. Idempotent: checks for
 * existing data before inserting, so restarting the stack never duplicates
 * anything. Expanded further in later phases to seed audit logs/notifications once
 * those modules exist.
 */
@Component
@Profile("dev")
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final RiskAssessmentPort riskAssessmentPort;
    private final TransactionStateMachine transactionStateMachine;
    private final SecureRandom random = new SecureRandom();

    private static final String DEMO_PASSWORD = "Password123";

    @Override
    @Transactional
    public void run(String... args) {
        seedUser("admin@platform.local", "Ada", "Admin", RoleName.ADMIN);
        seedUser("employee@platform.local", "Eli", "Employee", RoleName.EMPLOYEE);
        User customer = seedUser("customer@platform.local", "Cara", "Customer", RoleName.CUSTOMER);

        if (customer != null && accountRepository.findByUserId(customer.getId()).isEmpty()) {
            Account checking = seedAccount(customer.getId(), AccountType.CHECKING, new BigDecimal("2500.00"));
            Account savings = seedAccount(customer.getId(), AccountType.SAVINGS, new BigDecimal("10000.00"));
            log.info("Seeded demo accounts for {}", customer.getEmail());

            seedPendingTransfer(customer.getId(), checking, savings);
        }
    }

    /**
     * Returns the existing or newly created user, or null if lookup/creation is
     * skipped for a reason other than "already exists" (should not happen here).
     */
    private User seedUser(String email, String firstName, String lastName, String roleName) {
        var existing = userRepository.findByEmail(email);
        if (existing.isPresent()) {
            return existing.get();
        }
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalStateException(roleName + " role is not seeded"));

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(DEMO_PASSWORD));
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setStatus(UserStatus.ACTIVE);
        user.getRoles().add(role);
        User saved = userRepository.save(user);

        log.info("Seeded demo {} account: {}", roleName, email);
        return saved;
    }

    private Account seedAccount(UUID userId, AccountType type, BigDecimal balance) {
        String accountNumber;
        do {
            accountNumber = String.valueOf(1_000_000_000L + (Math.abs(random.nextLong()) % 9_000_000_000L));
        } while (accountRepository.existsByAccountNumber(accountNumber));

        Account account = new Account();
        account.setUserId(userId);
        account.setAccountNumber(accountNumber);
        account.setAccountType(type);
        account.setBalance(balance);
        account.setStatus(AccountStatus.ACTIVE);
        return accountRepository.save(account);
    }

    /**
     * Mirrors the real TransferService.createTransfer() flow (create as SUBMITTED,
     * risk-assess, transition to PENDING_APPROVAL or FAILED) rather than just
     * poking a final status directly into the row - runs before RiskPolicySeeder
     * (@Order(3)) so no LimitPolicy rows exist yet, meaning this assessment always
     * comes back clean (LOW, not blocked), same as it would for the very first
     * real transfer on a freshly-booted system with no policies configured yet.
     */
    private void seedPendingTransfer(UUID customerUserId, Account source, Account destination) {
        Transaction transaction = new Transaction();
        transaction.setSourceAccountId(source.getId());
        transaction.setDestinationAccountNumber(destination.getAccountNumber());
        transaction.setAmount(new BigDecimal("150.00"));
        transaction.setIdempotencyKey("seed-transfer-" + customerUserId);
        transaction.setInitiatedByUserId(customerUserId);
        transaction.setNote("Sample transfer awaiting employee approval");
        transaction.setStatus(TransactionStatus.SUBMITTED);
        Transaction saved = transactionRepository.save(transaction);
        transactionStateMachine.recordInitialState(saved, customerUserId);

        RiskAssessmentResult riskResult = riskAssessmentPort.assessAndRecord(
                saved.getId(), source.getId(), customerUserId, saved.getAmount(), saved.getCurrency());

        if (riskResult.blocked()) {
            transactionStateMachine.transition(saved, TransactionStatus.SUBMITTED, TransactionStatus.FAILED, customerUserId);
        } else {
            transactionStateMachine.transition(saved, TransactionStatus.SUBMITTED, TransactionStatus.PENDING_APPROVAL, customerUserId);
            // Mirrors TransferService.createTransfer()'s SLA assignment exactly (same
            // shared helper, keyed off this same assessment's risk level), so the
            // seeded item behaves identically to a real one in the Approval
            // Workbench rather than looking like a special case with no deadline.
            saved.setSlaDueAt(Instant.now().plus(TransferService.approvalSlaWindow(riskResult.riskLevel())));
            saved = transactionRepository.save(saved);
        }

        log.info("Seeded a sample transfer ({}) for the employee approval queue", saved.getStatus());
    }
}

