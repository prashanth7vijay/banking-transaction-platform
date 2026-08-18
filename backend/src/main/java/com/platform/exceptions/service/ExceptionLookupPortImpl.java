package com.platform.exceptions.service;

import com.platform.exceptions.domain.TransactionException;
import com.platform.exceptions.port.ExceptionLookupPort;
import com.platform.exceptions.port.ExceptionSummary;
import com.platform.exceptions.repository.TransactionExceptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExceptionLookupPortImpl implements ExceptionLookupPort {

    private final TransactionExceptionRepository transactionExceptionRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ExceptionSummary> listForTransactionIds(List<UUID> transactionIds) {
        if (transactionIds.isEmpty()) {
            return List.of();
        }
        return transactionExceptionRepository.findByTransactionIdIn(transactionIds).stream()
                .map(this::toSummary)
                .toList();
    }

    private ExceptionSummary toSummary(TransactionException exception) {
        return new ExceptionSummary(
                exception.getId(),
                exception.getTransactionId(),
                exception.getStatus().name(),
                exception.getPriority().name(),
                exception.getReason(),
                exception.getAssignedToUserId(),
                exception.getCreatedAt()
        );
    }
}
