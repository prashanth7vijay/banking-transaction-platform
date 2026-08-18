package com.platform.accounts.controller;

import com.platform.accounts.dto.AccountResponse;
import com.platform.accounts.dto.OpenAccountRequest;
import com.platform.accounts.mapper.AccountMapper;
import com.platform.accounts.service.AccountService;
import com.platform.auth.security.CurrentUserProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/employee/accounts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('EMPLOYEE')")
public class EmployeeAccountController {

    private final AccountService accountService;
    private final AccountMapper accountMapper;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping
    public ResponseEntity<AccountResponse> openAccount(@Valid @RequestBody OpenAccountRequest request) {
        var employeeId = currentUserProvider.get().userId();
        var account = accountService.openAccountForCustomer(
                employeeId, request.customerUserId(), request.accountType(), request.openingBalance());
        return ResponseEntity.status(HttpStatus.CREATED).body(accountMapper.toResponse(account));
    }
}
