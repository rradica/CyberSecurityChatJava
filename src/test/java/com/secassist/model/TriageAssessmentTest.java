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
}
