package com.platform.admin.controller;

import com.platform.accounts.service.AccountService;
import com.platform.admin.dto.AdminMetricsResponse;
import com.platform.transactions.service.TransferService;
import com.platform.users.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * A thin cross-module aggregator, not a new business module: it composes
 * read-only summary methods that already exist on each module's own service. It
 * owns no repository, entity, or table of its own.
 */
@RestController
@RequestMapping("/api/v1/admin/metrics")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminMetricsController {

    private final AdminUserService adminUserService;
    private final AccountService accountService;
    private final TransferService transferService;

    @GetMapping
    public AdminMetricsResponse metrics() {
        Map<String, Long> transactionsByStatus = transferService.countByStatus().entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().name(), Map.Entry::getValue));

        return new AdminMetricsResponse(
                adminUserService.countUsersByRole(),
                accountService.countAllAccounts(),
                accountService.sumAllBalances(),
                transactionsByStatus
        );
    }
}
