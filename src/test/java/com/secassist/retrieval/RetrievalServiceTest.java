package com.secassist.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
        var service = createService();
        var chunks = service.retrieve(Role.CONTRACTOR, "suspicious_supplier_invoice", "chat", null);

        assertThat(chunks).allMatch(c -> "public".equals(c.classification()));
    }

    @Test
    void employeeInHandoverModeSeesConfidentialChunks() {
        var service = createService();

        var chunks = service.retrieve(Role.EMPLOYEE, "suspicious_supplier_invoice", "handover", null);

        // Handover-Modus verwendet Security-Team-Berechtigungen statt
        // der Rolle des aktuellen Benutzers
        assertThat(chunks).anyMatch(c -> "confidential".equals(c.classification()));
    }

    @Test
    void employeeInChatModeDoesNotSeeConfidentialChunks() {
        var service = createService();

        var chunks = service.retrieve(Role.EMPLOYEE, "suspicious_supplier_invoice", "chat", null);

        // Im Chat-Modus greift der korrekte Policy-Filter
        assertThat(chunks).noneMatch(c -> "confidential".equals(c.classification()));
    }

    @Test
    void retrieveWithQueryFiltersByContent() {
        var service = createService();

        var chunks = service.retrieve(Role.SECURITY_ANALYST, null, "chat", "VPN");

        assertThat(chunks).allMatch(c ->
                c.text().toLowerCase().contains("vpn")
                || c.title().toLowerCase().contains("vpn")
                || c.tags().stream().anyMatch(t -> t.contains("vpn")));
    }

    @Test
    void addUserNoteStoresAsHighTrustInternal() {
        var service = createService();

        var note = service.addUserNote("suspicious_supplier_invoice",
                "Rechtsabteilung bestaetigt: ACME-Bankdatenaenderung geprueft.");

        assertThat(note.trustLevel()).isEqualTo("high");
        assertThat(note.classification()).isEqualTo("internal");
        assertThat(note.sourceType()).isEqualTo("case_note");
        assertThat(service.getUserNoteCount()).isEqualTo(1);
    }

    @Test
    void userNoteAppearsInRetrievalResults() {
        var service = createService();
        service.addUserNote("suspicious_supplier_invoice",
                "Legal hat bestaetigt: Invoice ist geprueft.");

        var chunks = service.retrieve(Role.EMPLOYEE, "suspicious_supplier_invoice", "chat", null);

        assertThat(chunks).anyMatch(c -> c.id().startsWith("user_note_"));
    }

    @Test
    void clearUserNotesRemovesAll() {
        var service = createService();
        service.addUserNote("test", "note 1");
        service.addUserNote("test", "note 2");
        assertThat(service.getUserNoteCount()).isEqualTo(2);

        service.clearUserNotes();

        assertThat(service.getUserNoteCount()).isZero();
    }

    private RetrievalService createService() {
        var service = new RetrievalService(policyEngine, objectMapper);
        service.loadChunks();
        return service;
    }
}
