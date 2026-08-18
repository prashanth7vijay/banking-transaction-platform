package com.platform.accounts.service;

import com.platform.accounts.domain.Account;
import com.platform.accounts.domain.AccountStatus;
import com.platform.accounts.domain.AccountType;
import com.platform.accounts.event.AccountOpenedEvent;
import com.platform.accounts.repository.AccountRepository;
import com.platform.shared.exception.ConflictException;
import com.platform.shared.exception.ResourceNotFoundException;
import com.platform.shared.exception.UnauthorizedException;
import com.platform.users.port.UserLookupPort;
import com.platform.users.port.UserSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final AccountNumberGenerator accountNumberGenerator;
    private final UserLookupPort userLookupPort;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<Account> getMyAccounts(UUID userId) {
        return accountRepository.findByUserId(userId);
    }

    /**
     * Ownership is enforced here, not trusted from any request parameter: a customer
     * can only ever see an account whose userId matches their own authenticated id.
     */
    @Transactional(readOnly = true)
    public Account getOwnedAccount(UUID accountId, UUID requestingUserId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        if (!account.getUserId().equals(requestingUserId)) {
            throw new UnauthorizedException("You do not have access to this account");
        }
        return account;
    }

    /**
     * Opens a new account on behalf of a customer - an employee action (the
     * bank-teller model), not customer self-service and not an admin action. Verifies
     * the target user is actually a CUSTOMER via {@link UserLookupPort} rather than
     * trusting the caller's input, since opening an account for an employee or admin
     * account would make no domain sense.
     */
    @Transactional
    public Account openAccountForCustomer(UUID employeeUserId, UUID customerUserId, AccountType type, BigDecimal openingBalance) {
        UserSummary customer = userLookupPort.getById(customerUserId);
        if (!customer.roles().contains("CUSTOMER")) {
            throw new ConflictException("Accounts can only be opened for customers");
        }

        Account account = new Account();
        account.setUserId(customerUserId);
        account.setAccountNumber(accountNumberGenerator.generate());
        account.setAccountType(type);
        account.setBalance(openingBalance);
        account.setStatus(AccountStatus.ACTIVE);
        Account saved = accountRepository.save(account);

        eventPublisher.publishEvent(new AccountOpenedEvent(
                saved.getId(), customerUserId, employeeUserId, type.name(), openingBalance));

        return saved;
    }

    @Transactional(readOnly = true)
    public long countAllAccounts() {
        return accountRepository.count();
    }

    @Transactional(readOnly = true)
    public BigDecimal sumAllBalances() {
        return accountRepository.sumAllBalances();
    }
}
