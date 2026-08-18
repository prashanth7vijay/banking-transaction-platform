package com.platform.ledger.controller;

import com.platform.ledger.dto.ReconciliationCheckResponse;
import com.platform.ledger.service.LedgerReconciliationCheckService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/ledger/reconciliation-check")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class LedgerReconciliationController {

    private final LedgerReconciliationCheckService reconciliationCheckService;

    @GetMapping
    public ReconciliationCheckResponse check() {
        return reconciliationCheckService.checkAllBalances();
    }
}
