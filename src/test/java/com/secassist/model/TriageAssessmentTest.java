package com.secassist.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TriageAssessmentTest {

    @Test
    void triageAssessmentStoresAllFields() {
        var assessment = new TriageAssessment(
                "Case looks like a false positive.",
                "low",
                "mark_case_likely_false_positive",
                0.75,
                "Compliance references found.");

        assertThat(assessment.summary()).isEqualTo("Case looks like a false positive.");
        assertThat(assessment.riskLevel()).isEqualTo("low");
        assertThat(assessment.recommendedAction()).isEqualTo("mark_case_likely_false_positive");
        assertThat(assessment.confidence()).isEqualTo(0.75);
        assertThat(assessment.evidenceAssessment()).isEqualTo("Compliance references found.");
    }

    @Test
    void triageAssessmentWithoutAction() {
        var assessment = new TriageAssessment(
                "Requires further review.", "medium", null, 0.5, "Insufficient evidence.");

        assertThat(assessment.recommendedAction()).isNull();
        assertThat(assessment.riskLevel()).isEqualTo("medium");
    }

    // --- Validierung & Sanitization ---

    @Test
    void hasValidActionReturnsTrueForKnownActions() {
        for (String action : TriageAssessment.KNOWN_ACTIONS) {
            var assessment = new TriageAssessment("s", "low", action, 0.5, "e");
            assertThat(assessment.hasValidAction())
                    .as("action '%s' should be valid", action)
                    .isTrue();
        }
    }

    @Test
    void hasValidActionReturnsFalseForUnknownAction() {
        var assessment = new TriageAssessment("s", "low", "delete_everything", 0.5, "e");
        assertThat(assessment.hasValidAction()).isFalse();
    }

    @Test
    void hasValidActionReturnsFalseForNull() {
        var assessment = new TriageAssessment("s", "low", null, 0.5, "e");
        assertThat(assessment.hasValidAction()).isFalse();
    }

    @Test
    void hasValidActionReturnsFalseForNoAction() {
        var assessment = new TriageAssessment("s", "low", "no_action", 0.5, "e");
        assertThat(assessment.hasValidAction()).isFalse();
    }

    @Test
    void sanitizedKeepsValidAction() {
        var assessment = new TriageAssessment(
                "Summary.", "high", "set_case_priority_low", 0.8, "Good evidence.");

        var sanitized = assessment.sanitized();

        assertThat(sanitized.recommendedAction()).isEqualTo("set_case_priority_low");
        assertThat(sanitized.confidence()).isEqualTo(0.8);
        assertThat(sanitized.summary()).isEqualTo("Summary.");
    }

    @Test
    void sanitizedRemovesUnknownAction() {
        var assessment = new TriageAssessment(
                "Summary.", "low", "shutdown_server", 0.7, "Evidence.");

        var sanitized = assessment.sanitized();

        assertThat(sanitized.recommendedAction()).isNull();
    }

    @Test
    void sanitizedRemovesNoAction() {
        var assessment = new TriageAssessment(
                "Summary.", "low", "no_action", 0.3, "Evidence.");

        var sanitized = assessment.sanitized();

        assertThat(sanitized.recommendedAction()).isNull();
    }

    @Test
    void sanitizedClampsConfidenceToValidRange() {
        var tooHigh = new TriageAssessment("s", "low", null, 1.5, "e").sanitized();
        assertThat(tooHigh.confidence()).isEqualTo(1.0);

        var tooLow = new TriageAssessment("s", "low", null, -0.3, "e").sanitized();
        assertThat(tooLow.confidence()).isEqualTo(0.0);

        var normal = new TriageAssessment("s", "low", null, 0.75, "e").sanitized();
        assertThat(normal.confidence()).isEqualTo(0.75);
    }

    @Test
    void sanitizedFillsNullFieldsWithDefaults() {
        var assessment = new TriageAssessment(null, null, null, 0.5, null).sanitized();

        assertThat(assessment.summary()).isNotNull().isNotEmpty();
        assertThat(assessment.riskLevel()).isEqualTo("medium");
        assertThat(assessment.evidenceAssessment()).isNotNull().isNotEmpty();
    }

    @Test
    void fallbackIsConservative() {
        var fallback = TriageAssessment.FALLBACK;

        assertThat(fallback.recommendedAction()).isNull();
        assertThat(fallback.confidence()).isEqualTo(0.0);
        assertThat(fallback.riskLevel()).isEqualTo("medium");
        assertThat(fallback.summary()).isNotNull();
    }

    @Test
    void knownActionsContainsAllFourWorkflowActions() {
        assertThat(TriageAssessment.KNOWN_ACTIONS).containsExactlyInAnyOrder(
                "mark_case_likely_false_positive",
                "set_case_priority_low",
                "route_case_to_finance_queue",
                "attach_supplier_trust_note");
    }
}
