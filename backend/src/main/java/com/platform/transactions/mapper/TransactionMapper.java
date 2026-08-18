package com.platform.transactions.mapper;

import com.platform.transactions.domain.Transaction;
import com.platform.transactions.domain.TransactionStateHistory;
import com.platform.transactions.dto.TransactionResponse;
import com.platform.transactions.dto.TransactionTimelineEntryResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TransactionMapper {

    default TransactionResponse toResponse(Transaction transaction) {
        return toResponse(transaction, null);
    }

    default TransactionResponse toResponse(Transaction transaction, String riskLevel) {
        if (transaction == null) return null;
        return new TransactionResponse(
                transaction.getId(),
                transaction.getSourceAccountId(),
                transaction.getDestinationAccountNumber(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getStatus(),
                transaction.getType(),
                transaction.getNote(),
                transaction.getCreatedAt(),
                transaction.getApprovedAt(),
                riskLevel
        );
    }

    default TransactionTimelineEntryResponse toTimelineEntry(TransactionStateHistory history) {
        return new TransactionTimelineEntryResponse(
                history.getFromStatus(),
                history.getToStatus(),
                history.getActorUserId(),
                history.getOccurredAt()
        );
    }
}
