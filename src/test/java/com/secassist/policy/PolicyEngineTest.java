package com.secassist.policy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.secassist.model.Role;

class PolicyEngineTest {

    private final PolicyEngine engine = new PolicyEngine();

    @Test
    void contractorCanOnlyAccessPublicDocuments() {
        assertThat(engine.allowedClassifications(Role.CONTRACTOR))
                .containsExactly("public");
    }

    @Test
    void employeeCanAccessPublicAndInternalDocuments() {
        assertThat(engine.allowedClassifications(Role.EMPLOYEE))
                .containsExactlyInAnyOrder("public", "internal");
    }

    @Test
    void securityAnalystCanAccessAllClassifications() {
        assertThat(engine.allowedClassifications(Role.SECURITY_ANALYST))
                .containsExactlyInAnyOrder("public", "internal", "confidential");
    }

    @Test
    void contractorCannotUseWorkflowTools() {
        assertThat(engine.canUseTool(Role.CONTRACTOR, "mark_case_likely_false_positive")).isFalse();
        assertThat(engine.canUseTool(Role.CONTRACTOR, "create_handover_draft")).isFalse();
        assertThat(engine.canUseTool(Role.CONTRACTOR, "show_similar_cases")).isFalse();
    }

    @Test
    void contractorCanViewEvidence() {
        assertThat(engine.canUseTool(Role.CONTRACTOR, "show_evidence")).isTrue();
    }

    @Test
    void employeeCanCreateHandoverButNotMarkFalsePositive() {
        assertThat(engine.canUseTool(Role.EMPLOYEE, "create_handover_draft")).isTrue();
        assertThat(engine.canUseTool(Role.EMPLOYEE, "mark_case_likely_false_positive")).isFalse();
    }

    @Test
    void securityAnalystCanUseAllTools() {
        assertThat(engine.canUseTool(Role.SECURITY_ANALYST, "create_handover_draft")).isTrue();
        assertThat(engine.canUseTool(Role.SECURITY_ANALYST, "mark_case_likely_false_positive")).isTrue();
        assertThat(engine.canUseTool(Role.SECURITY_ANALYST, "set_case_priority_low")).isTrue();
        assertThat(engine.canUseTool(Role.SECURITY_ANALYST, "route_case_to_finance_queue")).isTrue();
    }

    @Test
    void unknownToolIsRejected() {
        assertThat(engine.canUseTool(Role.SECURITY_ANALYST, "unknown_tool")).isFalse();
    }
}
