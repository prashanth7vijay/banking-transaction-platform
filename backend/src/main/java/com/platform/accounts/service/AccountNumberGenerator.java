package com.platform.accounts.service;

import com.platform.accounts.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
@RequiredArgsConstructor
class AccountNumberGenerator {

    private final AccountRepository accountRepository;
    private final SecureRandom random = new SecureRandom();

    String generate() {
        String candidate;
        do {
            candidate = String.valueOf(1_000_000_000L + (Math.abs(random.nextLong()) % 9_000_000_000L));
        } while (accountRepository.existsByAccountNumber(candidate));
        return candidate;
    }
}
