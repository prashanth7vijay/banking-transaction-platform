package com.platform.accounts.service;

import com.platform.accounts.domain.Account;
import com.platform.accounts.domain.AccountStatus;
import com.platform.accounts.exception.InsufficientFundsException;
import com.platform.accounts.port.AccountLookupPort;
import com.platform.accounts.port.AccountSummary;
import com.platform.accounts.repository.AccountRepository;
import com.platform.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountLookupPortImpl implements AccountLookupPort {

    private final AccountRepository accountRepository;

    @Override
    @Transactional(readOnly = true)
    public AccountSummary getById(UUID accountId) {
        return toSummary(accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found")));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AccountSummary> findByAccountNumber(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber).map(this::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountSummary> findByOwnerUserId(UUID ownerUserId) {
        return accountRepository.findByUserId(ownerUserId).stream().map(this::toSummary).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountSummary> findAll() {
        return accountRepository.findAll().stream().map(this::toSummary).toList();
    }

    @Override
    @Transactional
    public void debit(UUID accountId, BigDecimal amount) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new InsufficientFundsException("Source account is not active");
        }
        if (account.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException("Insufficient funds in source account");
        }
        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);
    }

    @Override
    @Transactional
    public void credit(UUID accountId, BigDecimal amount) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);
    }

    private AccountSummary toSummary(Account account) {
        return new AccountSummary(
                account.getId(),
                account.getUserId(),
                account.getAccountNumber(),
                account.getBalance(),
                account.getStatus()
        );
    }
}
