package com.platform.accounts.controller;

import com.platform.accounts.dto.BeneficiaryResponse;
import com.platform.accounts.dto.CreateBeneficiaryRequest;
import com.platform.accounts.mapper.AccountMapper;
import com.platform.accounts.service.BeneficiaryService;
import com.platform.auth.security.CurrentUserProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/beneficiaries")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CUSTOMER')")
public class BeneficiaryController {

    private final BeneficiaryService beneficiaryService;
    private final AccountMapper accountMapper;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public List<BeneficiaryResponse> list() {
        UUID userId = currentUserProvider.get().userId();
        return beneficiaryService.list(userId).stream().map(accountMapper::toResponse).toList();
    }

    @PostMapping
    public ResponseEntity<BeneficiaryResponse> add(@Valid @RequestBody CreateBeneficiaryRequest request) {
        UUID userId = currentUserProvider.get().userId();
        var beneficiary = beneficiaryService.add(userId, request.beneficiaryAccountNumber(), request.nickname());
        return ResponseEntity.status(HttpStatus.CREATED).body(accountMapper.toResponse(beneficiary));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(@PathVariable UUID id) {
        UUID userId = currentUserProvider.get().userId();
        beneficiaryService.remove(userId, id);
        return ResponseEntity.noContent().build();
    }
}
