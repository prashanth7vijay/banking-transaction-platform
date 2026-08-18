package com.platform.risk.repository;

import com.platform.risk.domain.LimitPolicy;
import com.platform.risk.domain.LimitScope;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LimitPolicyRepository extends JpaRepository<LimitPolicy, java.util.UUID> {
    List<LimitPolicy> findByScopeAndActiveTrue(LimitScope scope);
}
