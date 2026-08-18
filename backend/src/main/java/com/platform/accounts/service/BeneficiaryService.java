package com.platform.accounts.service;

import com.platform.accounts.domain.Beneficiary;
import com.platform.accounts.repository.BeneficiaryRepository;
import com.platform.shared.exception.ConflictException;
import com.platform.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BeneficiaryService {

    private final BeneficiaryRepository beneficiaryRepository;

    @Transactional(readOnly = true)
    public List<Beneficiary> list(UUID ownerUserId) {
        return beneficiaryRepository.findByOwnerUserId(ownerUserId);
    }

    @Transactional
    public Beneficiary add(UUID ownerUserId, String accountNumber, String nickname) {
        Beneficiary beneficiary = new Beneficiary();
        beneficiary.setOwnerUserId(ownerUserId);
        beneficiary.setBeneficiaryAccountNumber(accountNumber);
        beneficiary.setNickname(nickname);
        try {
            return beneficiaryRepository.save(beneficiary);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("This account is already saved as a beneficiary");
        }
    }

    @Transactional
    public void remove(UUID ownerUserId, UUID beneficiaryId) {
        Beneficiary beneficiary = beneficiaryRepository.findByIdAndOwnerUserId(beneficiaryId, ownerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Beneficiary not found"));
        beneficiaryRepository.delete(beneficiary);
    }
}
