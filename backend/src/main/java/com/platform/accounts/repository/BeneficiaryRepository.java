package com.platform.accounts.repository;

import com.platform.accounts.domain.Beneficiary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BeneficiaryRepository extends JpaRepository<Beneficiary, UUID> {
    List<Beneficiary> findByOwnerUserId(UUID ownerUserId);
    Optional<Beneficiary> findByIdAndOwnerUserId(UUID id, UUID ownerUserId);
}
