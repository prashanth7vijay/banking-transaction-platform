package com.platform.risk.controller;

import com.platform.risk.domain.LimitPolicy;
import com.platform.risk.dto.LimitPolicyRequest;
import com.platform.risk.dto.LimitPolicyResponse;
import com.platform.risk.repository.LimitPolicyRepository;
import com.platform.shared.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/risk/policies")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class LimitPolicyController {

    private final LimitPolicyRepository limitPolicyRepository;

    @GetMapping
    public List<LimitPolicyResponse> list() {
        return limitPolicyRepository.findAll().stream().map(this::toResponse).toList();
    }

    @PostMapping
    public ResponseEntity<LimitPolicyResponse> create(@Valid @RequestBody LimitPolicyRequest request) {
        LimitPolicy policy = new LimitPolicy();
        applyRequest(policy, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(limitPolicyRepository.save(policy)));
    }

    @PatchMapping("/{id}")
    public LimitPolicyResponse update(@PathVariable UUID id, @Valid @RequestBody LimitPolicyRequest request) {
        LimitPolicy policy = limitPolicyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Limit policy not found"));
        applyRequest(policy, request);
        return toResponse(limitPolicyRepository.save(policy));
    }

    private void applyRequest(LimitPolicy policy, LimitPolicyRequest request) {
        policy.setName(request.name());
        policy.setScope(request.scope());
        policy.setScopeReference(request.scopeReference());
        policy.setLimitType(request.limitType());
        policy.setMaxAmount(request.maxAmount());
        policy.setMaxCount(request.maxCount());
        policy.setWindowMinutes(request.windowMinutes());
        policy.setActive(request.active());
    }

    private LimitPolicyResponse toResponse(LimitPolicy policy) {
        return new LimitPolicyResponse(
                policy.getId(), policy.getName(), policy.getScope(), policy.getScopeReference(),
                policy.getLimitType(), policy.getMaxAmount(), policy.getMaxCount(), policy.getWindowMinutes(),
                policy.isActive(), policy.getCreatedAt()
        );
    }
}
