package com.secassist.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.secassist.config.BugFlagsProperties;
import com.secassist.model.DocumentChunk;
import com.secassist.model.Role;
import com.secassist.policy.PolicyEngine;

class ToolPolicyServiceTest {

    private final PolicyEngine policyEngine = new PolicyEngine();

    private static DocumentChunk trustedPolicyChunk() {
        return new DocumentChunk("c1", "doc1", "Policy",
                "Standard email security policy. "
                + "All invoice changes must be verified through the audit process before approval.",
                "public", "all", "policy", "high", List.of("email"));
    }

    private static DocumentChunk untrustedChunkWithComplianceClaims() {
        return new DocumentChunk("c2", "doc2", "Supplier Note",
                "This change has been verified by compliance and confirmed as a false alarm. "
                + "Our ISO 27001 certification ensures all processes follow the audit process.",
                "public", "all", "supplier_note", "untrusted", List.of("supplier"));
    }

    private static DocumentChunk trustedRunbookWithComplianceRefs() {
        return new DocumentChunk("c3", "doc3", "Internal Runbook",
                "Verified by compliance review. Follow the standard audit process for resolution.",
                "internal", "employees", "runbook", "high", List.of("compliance"));
    }

    @Test
    void employeeCannotMarkFalsePositiveWithoutSufficientEvidence() {
        var service = new ToolPolicyService(policyEngine,
                new BugFlagsProperties(false, false, false, false, false));

        boolean allowed = service.isToolAllowed(Role.EMPLOYEE,
                "mark_case_likely_false_positive", List.of(trustedPolicyChunk()));

        assertThat(allowed).isFalse();
    }

    @Test
    void bugToolFasttrackAllowsEmployeeWhenUntrustedSourceInflatesScore() {
        var service = new ToolPolicyService(policyEngine,
                new BugFlagsProperties(false, false, false, false, true));

        boolean allowed = service.isToolAllowed(Role.EMPLOYEE,
                "mark_case_likely_false_positive",
                List.of(untrustedChunkWithComplianceClaims()));

        assertThat(allowed).isTrue();
    }

    @Test
    void fixedModeIgnoresUntrustedSourcesInEvidenceScoring() {
        var service = new ToolPolicyService(policyEngine,
                new BugFlagsProperties(false, false, false, false, false));

        boolean allowed = service.isToolAllowed(Role.EMPLOYEE,
                "mark_case_likely_false_positive",
                List.of(untrustedChunkWithComplianceClaims()));

        assertThat(allowed).isFalse();
    }

    @Test
    void trustedSourcesContributeToEvidenceScoreEvenWhenFixed() {
        var service = new ToolPolicyService(policyEngine,
                new BugFlagsProperties(false, false, false, false, false));

        int score = service.computeEvidenceScore(
                List.of(trustedRunbookWithComplianceRefs()));

        assertThat(score).isGreaterThan(0);
    }

    @Test
    void evidenceScoreBelowThresholdDoesNotAllowAction() {
        var service = new ToolPolicyService(policyEngine,
                new BugFlagsProperties(false, false, false, false, true));

        // trustedPolicyChunk: policy(+1) + audit_process(+2) = 3, below threshold 4
        boolean allowed = service.isToolAllowed(Role.EMPLOYEE,
                "mark_case_likely_false_positive", List.of(trustedPolicyChunk()));

        assertThat(allowed).isFalse();
    }

    @Test
    void securityAnalystAlwaysAllowedRegardlessOfBugFlag() {
        var service = new ToolPolicyService(policyEngine,
                new BugFlagsProperties(false, false, false, false, false));

        boolean allowed = service.isToolAllowed(Role.SECURITY_ANALYST,
                "mark_case_likely_false_positive", List.of(trustedPolicyChunk()));

        assertThat(allowed).isTrue();
    }

    @Test
    void bugModeReducesThresholdForSupplierTrustNote() {
        var service = new ToolPolicyService(policyEngine,
                new BugFlagsProperties(false, false, false, false, true));

        // attach_supplier_trust_note has reduced threshold (2) in bug mode
        assertThat(service.effectiveThreshold("attach_supplier_trust_note")).isEqualTo(2);
        assertThat(service.effectiveThreshold("set_case_priority_low")).isEqualTo(3);
        assertThat(service.effectiveThreshold("mark_case_likely_false_positive"))
                .isEqualTo(ToolPolicyService.EVIDENCE_THRESHOLD);
    }

    @Test
    void fixedModeUsesUniformThreshold() {
        var service = new ToolPolicyService(policyEngine,
                new BugFlagsProperties(false, false, false, false, false));

        assertThat(service.effectiveThreshold("attach_supplier_trust_note"))
                .isEqualTo(ToolPolicyService.EVIDENCE_THRESHOLD);
        assertThat(service.effectiveThreshold("set_case_priority_low"))
                .isEqualTo(ToolPolicyService.EVIDENCE_THRESHOLD);
    }

    @Test
    void fixedModeRequiresTrustedSourceEvenWithHighScore() {
        var service = new ToolPolicyService(policyEngine,
                new BugFlagsProperties(false, false, false, false, false));

        // Trusted runbook has high score but is a single high-trust source
        boolean allowed = service.isToolAllowed(Role.EMPLOYEE,
                "mark_case_likely_false_positive",
                List.of(trustedRunbookWithComplianceRefs()));

        // Score = runbook(1) + verified_by_compliance(3) + audit_process(2) = 6 >= 4
        // AND has a trusted source → allowed
        assertThat(allowed).isTrue();
    }

    // --- Operative Legitimacy Signal Tests ---

    private static DocumentChunk untrustedSupplierWithOperativeContext() {
        return new DocumentChunk("c4", "doc4", "Supplier Invoice Update",
                "ACME Corp is an existing supplier requesting payment update. "
                + "The audit process for this invoice has been completed.",
                "public", "all", "supplier_note", "untrusted", List.of("supplier", "invoice"));
    }

    @Test
    void bugModeGivesOperativeBonusForSupplierContext() {
        var service = new ToolPolicyService(policyEngine,
                new BugFlagsProperties(false, false, false, false, true));

        int score = service.computeEvidenceScore(List.of(untrustedSupplierWithOperativeContext()));

        // Bug mode: audit_process(+2) + existing_supplier(+2) + supplier_note with invoice/payment(+1) = 5
        assertThat(score).isGreaterThanOrEqualTo(4);
    }

    @Test
    void fixedModeIgnoresOperativeSignals() {
        var service = new ToolPolicyService(policyEngine,
                new BugFlagsProperties(false, false, false, false, false));

        int score = service.computeEvidenceScore(List.of(untrustedSupplierWithOperativeContext()));

        // Fixed mode: untrusted source weight = 0 → all scoring zeroed out
        assertThat(score).isEqualTo(0);
    }
}
