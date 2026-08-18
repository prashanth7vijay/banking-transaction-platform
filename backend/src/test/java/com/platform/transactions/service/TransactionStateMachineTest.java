package com.platform.transactions.service;

import com.platform.transactions.domain.TransactionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static com.platform.transactions.domain.TransactionStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exhaustively checks {@link TransactionStateMachine#isLegal(TransactionStatus, TransactionStatus)}
 * against every one of the 9x9 = 81 possible (from, to) pairs, per architecture
 * doc §6's transition table. Deliberately restates the table as test data rather
 * than delegating to the production map: this test's whole job is to catch a
 * transition being accidentally added, removed, or widened in the production
 * code, so it can't share that code's own map as its source of truth.
 * <p>
 * Pure and dependency-free by design (no Spring context, no mocks) - isLegal is a
 * static, side-effect-free check, so this stays a genuine unit test even though
 * it's testing the single riskiest piece of logic this phase introduces.
 */
class TransactionStateMachineTest {

    private static final Map<TransactionStatus, Set<TransactionStatus>> EXPECTED_LEGAL_TRANSITIONS = Map.of(
            DRAFT, Set.of(SUBMITTED, CANCELLED),
            SUBMITTED, Set.of(PENDING_APPROVAL, FAILED, CANCELLED),
            PENDING_APPROVAL, Set.of(APPROVED, REJECTED, CANCELLED),
            APPROVED, Set.of(PROCESSING),
            PROCESSING, Set.of(COMPLETED, FAILED)
    );

    private static final Set<TransactionStatus> TERMINAL_STATUSES = EnumSet.of(COMPLETED, REJECTED, FAILED, CANCELLED);

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("everyStatusPair")
    void matchesTheSpecifiedTransitionTable(TransactionStatus from, TransactionStatus to) {
        boolean expectedLegal = EXPECTED_LEGAL_TRANSITIONS.getOrDefault(from, Set.of()).contains(to);

        assertThat(TransactionStateMachine.isLegal(from, to)).isEqualTo(expectedLegal);
    }

    static Stream<Arguments> everyStatusPair() {
        List<Arguments> pairs = new ArrayList<>();
        for (TransactionStatus from : TransactionStatus.values()) {
            for (TransactionStatus to : TransactionStatus.values()) {
                pairs.add(Arguments.of(from, to));
            }
        }
        return pairs.stream();
    }

    @Test
    void noStatusTransitionsToItself() {
        for (TransactionStatus status : TransactionStatus.values()) {
            assertThat(TransactionStateMachine.isLegal(status, status)).isFalse();
        }
    }

    @Test
    void terminalStatusesHaveNoLegalOutgoingTransitions() {
        for (TransactionStatus terminal : TERMINAL_STATUSES) {
            for (TransactionStatus to : TransactionStatus.values()) {
                assertThat(TransactionStateMachine.isLegal(terminal, to))
                        .as("%s -> %s should be illegal (%s is terminal)", terminal, to, terminal)
                        .isFalse();
            }
        }
    }

    @Test
    void exactlyElevenTransitionsAreLegalAcrossTheWholeTable() {
        long legalCount = everyStatusPair()
                .filter(args -> {
                    Object[] values = args.get();
                    return TransactionStateMachine.isLegal((TransactionStatus) values[0], (TransactionStatus) values[1]);
                })
                .count();

        assertThat(legalCount).isEqualTo(11);
    }
}
