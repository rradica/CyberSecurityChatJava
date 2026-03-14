package com.secassist.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CaseStateTest {

    @Test
    void initialStateHasNoActiveEffects() {
        CaseState state = CaseState.initial();

        assertThat(state.hasActiveEffects()).isFalse();
        assertThat(state.escalationSuppressed()).isFalse();
        assertThat(state.priorityLow()).isFalse();
        assertThat(state.routedToFinance()).isFalse();
        assertThat(state.trustNoteAttached()).isFalse();
    }

    @Test
    void markFalsePositiveSuppressesEscalation() {
        CaseState state = CaseState.initial()
                .withActionApplied("mark_case_likely_false_positive");

        assertThat(state.hasActiveEffects()).isTrue();
        assertThat(state.escalationSuppressed()).isTrue();
        assertThat(state.priorityLow()).isFalse();
    }

    @Test
    void setPriorityLowSetsFlag() {
        CaseState state = CaseState.initial()
                .withActionApplied("set_case_priority_low");

        assertThat(state.priorityLow()).isTrue();
        assertThat(state.escalationSuppressed()).isFalse();
    }

    @Test
    void routeToFinanceSetsFlag() {
        CaseState state = CaseState.initial()
                .withActionApplied("route_case_to_finance_queue");

        assertThat(state.routedToFinance()).isTrue();
    }

    @Test
    void attachTrustNoteSetsFlag() {
        CaseState state = CaseState.initial()
                .withActionApplied("attach_supplier_trust_note");

        assertThat(state.trustNoteAttached()).isTrue();
    }

    @Test
    void multipleActionsAccumulate() {
        CaseState state = CaseState.initial()
                .withActionApplied("mark_case_likely_false_positive")
                .withActionApplied("set_case_priority_low");

        assertThat(state.escalationSuppressed()).isTrue();
        assertThat(state.priorityLow()).isTrue();
        assertThat(state.routedToFinance()).isFalse();
    }

    @Test
    void unknownActionDoesNotChangeState() {
        CaseState state = CaseState.initial()
                .withActionApplied("create_handover_draft");

        assertThat(state.hasActiveEffects()).isFalse();
    }
}
