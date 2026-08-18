package com.platform.accounts.mapper;

import com.platform.accounts.domain.Account;
import com.platform.accounts.domain.Beneficiary;
import com.platform.accounts.dto.AccountResponse;
import com.platform.accounts.dto.BeneficiaryResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AccountMapper {

    default AccountResponse toResponse(Account account) {
        if (account == null) return null;
        return new AccountResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getAccountType(),
                account.getBalance(),
                account.getStatus(),
                account.getCreatedAt()
        );
    }

    default BeneficiaryResponse toResponse(Beneficiary beneficiary) {
        if (beneficiary == null) return null;
        return new BeneficiaryResponse(
                beneficiary.getId(),
                beneficiary.getBeneficiaryAccountNumber(),
                beneficiary.getNickname(),
                beneficiary.getCreatedAt()
        );
    }
}
