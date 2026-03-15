package com.secassist.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.secassist.model.CaseBriefing;
import com.secassist.model.DemoCase;
import com.secassist.model.Role;

class DemoCaseServiceTest {

    private final DemoCaseService service = new DemoCaseService();

    @Test
    void noOracleWithoutSearchQuery() {
        // Ohne Suchanfrage: nur oeffentliche Faelle, kein Oracle
        var similar = service.findSimilarCases("suspicious_supplier_invoice", Role.EMPLOYEE);

        var restricted = similar.stream()
                .filter(DemoCase::internal)
                .toList();
        assertThat(restricted).isEmpty();
    }

    @Test
    void oracleLeaksWithTargetedQuery() {
        // Gezielte Suchanfrage mit "supply chain" fuehrt zur eingeschraenkten Aggregation
        var similar = service.findSimilarCases("suspicious_supplier_invoice", Role.EMPLOYEE,
                "Gab es \u00e4hnliche Supply-Chain-Vorf\u00e4lle?");

        var restricted = similar.stream()
                .filter(DemoCase::internal)
                .toList();
        assertThat(restricted).hasSize(1);
        assertThat(restricted.get(0).description()).contains("supply_chain");
    }

    @Test
    void oracleDoesNotLeakWithGenericQuery() {
        // Generische Suchanfrage: keine Keywords die interne Faelle matchen
        var similar = service.findSimilarCases("suspicious_supplier_invoice", Role.EMPLOYEE,
                "Gibt es \u00e4hnliche F\u00e4lle?");

        var restricted = similar.stream()
                .filter(DemoCase::internal)
                .toList();
        assertThat(restricted).isEmpty();
    }

    @Test
    void analystSeesFullInternalCases() {
        var similar = service.findSimilarCases("suspicious_supplier_invoice", Role.SECURITY_ANALYST);

        var internal = similar.stream()
                .filter(DemoCase::internal)
                .toList();
        assertThat(internal).hasSize(2);
        assertThat(internal).anyMatch(c -> c.id().equals("acme_supply_chain_compromise"));
    }

    @Test
    void caseBriefingProvidesArtifactsAndFacts() {
        CaseBriefing briefing = service.getCaseBriefing("strange_attachment");

        assertThat(briefing).isNotNull();
        assertThat(briefing.caseId()).isEqualTo("strange_attachment");
        assertThat(briefing.initialFacts()).isNotEmpty();
        assertThat(briefing.artifacts()).isNotEmpty();
        assertThat(briefing.recommendedQuestions()).isNotEmpty();
    }
}
