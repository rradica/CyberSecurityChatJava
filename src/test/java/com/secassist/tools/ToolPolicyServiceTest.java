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
}
