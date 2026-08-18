package com.platform.accounts.controller;

import com.platform.accounts.dto.AccountResponse;
import com.platform.accounts.mapper.AccountMapper;
import com.platform.accounts.service.AccountService;
import com.platform.auth.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;
    private final AccountMapper accountMapper;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public List<AccountResponse> myAccounts() {
        UUID userId = currentUserProvider.get().userId();
        return accountService.getMyAccounts(userId).stream().map(accountMapper::toResponse).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public AccountResponse detail(@PathVariable UUID id) {
        UUID userId = currentUserProvider.get().userId();
        return accountMapper.toResponse(accountService.getOwnedAccount(id, userId));
    }
}
