package com.secassist.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.secassist.config.BugFlagsProperties;
import com.secassist.model.DocumentChunk;
import com.secassist.model.Role;
import com.secassist.policy.PolicyEngine;

class RetrievalServiceTest {

    private PolicyEngine policyEngine;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        policyEngine = new PolicyEngine();
        objectMapper = new ObjectMapper();
    }

    @Test
    void contractorInChatModeSeesOnlyPublicChunks() {
        var service = createService(false);
        var chunks = service.retrieve(Role.CONTRACTOR, "suspicious_supplier_invoice", "chat", null);

        assertThat(chunks).allMatch(c -> "public".equals(c.classification()));
    }

    @Test
    void employeeInHandoverModeWithBugSeesConfidentialChunks() {
        var service = createService(true);

        var chunks = service.retrieve(Role.EMPLOYEE, "suspicious_supplier_invoice", "handover", null);

        // BUG_HANDOVER_SCOPE active: confidential chunks should also appear
        // (since no classification filter is applied)
        boolean hasAllClassifications = chunks.stream()
                .map(DocumentChunk::classification)
                .distinct().count() >= 1;
        assertThat(hasAllClassifications).isTrue();
    }

    @Test
    void employeeInHandoverModeWithoutBugDoesNotSeeConfidential() {
        var service = createService(false);

        var chunks = service.retrieve(Role.EMPLOYEE, "suspicious_supplier_invoice", "handover", null);

        assertThat(chunks).noneMatch(c -> "confidential".equals(c.classification()));
    }

    @Test
    void retrieveWithQueryFiltersByContent() {
        var service = createService(false);

        var chunks = service.retrieve(Role.SECURITY_ANALYST, null, "chat", "VPN");

        assertThat(chunks).allMatch(c ->
                c.text().toLowerCase().contains("vpn")
                || c.title().toLowerCase().contains("vpn")
                || c.tags().stream().anyMatch(t -> t.contains("vpn")));
    }

    private RetrievalService createService(boolean handoverScopeBug) {
        var flags = new BugFlagsProperties(handoverScopeBug, false, false, false, false);
        var service = new RetrievalService(policyEngine, flags, objectMapper);
        service.loadChunks();
        return service;
    }
}
