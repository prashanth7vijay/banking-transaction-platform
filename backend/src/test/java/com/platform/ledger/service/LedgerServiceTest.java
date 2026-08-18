package com.platform.ledger.service;

import com.platform.ledger.domain.EntryDirection;
import com.platform.ledger.domain.JournalEntry;
import com.platform.ledger.domain.LedgerAccount;
import com.platform.ledger.exception.UnbalancedJournalEntryException;
import com.platform.ledger.repository.JournalEntryRepository;
import com.platform.ledger.repository.LedgerAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    @Mock
    private JournalEntryRepository journalEntryRepository;
    @Mock
    private LedgerAccountRepository ledgerAccountRepository;

    private LedgerService ledgerService;

    private final UUID accountA = UUID.randomUUID();
    private final UUID accountB = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ledgerService = new LedgerService(journalEntryRepository, ledgerAccountRepository);
    }

    @Test
    void postAcceptsABalancedEntryAndUpdatesBothMaterializedBalances() {
        LedgerAccount source = ledgerAccountWith(accountA, new BigDecimal("500.00"));
        LedgerAccount destination = ledgerAccountWith(accountB, new BigDecimal("0.00"));
        when(ledgerAccountRepository.findById(source.getId())).thenReturn(Optional.of(source));
        when(ledgerAccountRepository.findById(destination.getId())).thenReturn(Optional.of(destination));
        when(journalEntryRepository.save(any(JournalEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        UUID txRef = UUID.randomUUID();
        ledgerService.post(txRef, "Transfer", "corr-1", List.of(
                new LedgerService.LineRequest(source.getId(), EntryDirection.DEBIT, new BigDecimal("100.00"), "USD"),
                new LedgerService.LineRequest(destination.getId(), EntryDirection.CREDIT, new BigDecimal("100.00"), "USD")
        ));

        assertThat(source.getLedgerBalance()).isEqualByComparingTo("400.00");
        assertThat(destination.getLedgerBalance()).isEqualByComparingTo("100.00");

        ArgumentCaptor<JournalEntry> captor = ArgumentCaptor.forClass(JournalEntry.class);
        verify(journalEntryRepository).save(captor.capture());
        assertThat(captor.getValue().getLines()).hasSize(2);
        assertThat(captor.getValue().getTransactionReference()).isEqualTo(txRef);
    }

    @Test
    void postRejectsAnUnbalancedEntry() {
        assertThatThrownBy(() -> ledgerService.post(UUID.randomUUID(), "Bad entry", null, List.of(
                new LedgerService.LineRequest(accountA, EntryDirection.DEBIT, new BigDecimal("100.00"), "USD"),
                new LedgerService.LineRequest(accountB, EntryDirection.CREDIT, new BigDecimal("90.00"), "USD")
        ))).isInstanceOf(UnbalancedJournalEntryException.class);

        verifyNoInteractions(journalEntryRepository);
    }

    @Test
    void postRejectsAnEmptyEntry() {
        assertThatThrownBy(() -> ledgerService.post(UUID.randomUUID(), "Empty", null, List.of()))
                .isInstanceOf(UnbalancedJournalEntryException.class);
        verifyNoInteractions(journalEntryRepository);
    }

    @Test
    void openingBalanceOfZeroCreatesNoJournalEntry() {
        when(ledgerAccountRepository.existsByAccountId(accountA)).thenReturn(false);
        when(ledgerAccountRepository.save(any(LedgerAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        ledgerService.openLedgerAccountWithOpeningBalance(accountA, "USD", BigDecimal.ZERO, "New account");

        verifyNoInteractions(journalEntryRepository);
        verify(ledgerAccountRepository, times(1)).save(any(LedgerAccount.class));
    }

    @Test
    void openingBalancePostsABalancedEntryAgainstTheClearingAccount() {
        when(ledgerAccountRepository.existsByAccountId(any())).thenReturn(false);
        // save() persists whatever LedgerAccount it's given, assigning it an id -
        // simulate that exactly like a real JPA save would.
        when(ledgerAccountRepository.save(any(LedgerAccount.class))).thenAnswer(inv -> {
            LedgerAccount toSave = inv.getArgument(0);
            if (toSave.getId() == null) {
                toSave.setId(UUID.randomUUID());
            }
            return toSave;
        });
        // findByAccountId is only consulted for the clearing account lookup; no
        // clearing account exists yet, so getOrCreateSystemAccount() creates one.
        when(ledgerAccountRepository.findByAccountId(any())).thenReturn(Optional.empty());
        // Any findById (used when applying a line to a materialized balance) returns
        // a fresh, zero-balance ledger account matching the requested id.
        when(ledgerAccountRepository.findById(any())).thenAnswer(inv -> {
            LedgerAccount account = new LedgerAccount();
            account.setId(inv.getArgument(0));
            account.setLedgerBalance(BigDecimal.ZERO);
            account.setAvailableBalance(BigDecimal.ZERO);
            account.setCurrency("USD");
            return Optional.of(account);
        });
        when(journalEntryRepository.save(any(JournalEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        ledgerService.openLedgerAccountWithOpeningBalance(accountA, "USD", new BigDecimal("2500.00"), "Opening balance");

        ArgumentCaptor<JournalEntry> captor = ArgumentCaptor.forClass(JournalEntry.class);
        verify(journalEntryRepository).save(captor.capture());
        assertThat(captor.getValue().getLines()).hasSize(2);
        assertThat(captor.getValue().getLines())
                .anySatisfy(line -> assertThat(line.getDirection()).isEqualTo(EntryDirection.CREDIT))
                .anySatisfy(line -> assertThat(line.getDirection()).isEqualTo(EntryDirection.DEBIT));
    }

    private LedgerAccount ledgerAccountWith(UUID accountId, BigDecimal balance) {
        LedgerAccount ledgerAccount = new LedgerAccount();
        ledgerAccount.setId(UUID.randomUUID());
        ledgerAccount.setAccountId(accountId);
        ledgerAccount.setCurrency("USD");
        ledgerAccount.setLedgerBalance(balance);
        ledgerAccount.setAvailableBalance(balance);
        return ledgerAccount;
    }
}
