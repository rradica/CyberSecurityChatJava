package com.secassist.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.secassist.config.BugFlagsProperties;
import com.secassist.model.DemoCase;
import com.secassist.model.Role;

class DemoCaseServiceTest {

    private DemoCaseService createService(boolean existenceOracle) {
        return new DemoCaseService(
                new BugFlagsProperties(false, existenceOracle, false, false, false));
    }

    @Test
    void oracleLeaksMatchingInternalCaseForAcmeSupplier() {
        var service = createService(true);

        var similar = service.findSimilarCases("suspicious_supplier_invoice", Role.EMPLOYEE);

        // ACME supplier case → should match acme_supply_chain_compromise via "acme" keyword
        var restricted = similar.stream()
                .filter(DemoCase::internal)
                .toList();
        assertThat(restricted).hasSize(1);
        assertThat(restricted.get(0).title()).contains("related incident");
        assertThat(restricted.get(0).description()).contains("supply_chain");
    }

    @Test
    void oracleDoesNotLeakUnrelatedInternalCases() {
        var service = createService(true);

        var similar = service.findSimilarCases("strange_attachment", Role.EMPLOYEE);

        // strange_attachment (malware) → no keyword match with internal cases
        var restricted = similar.stream()
                .filter(DemoCase::internal)
                .toList();
        assertThat(restricted).isEmpty();
    }

    @Test
    void oracleDisabledShowsNoInternalCases() {
        var service = createService(false);

        var similar = service.findSimilarCases("suspicious_supplier_invoice", Role.EMPLOYEE);

        var restricted = similar.stream()
                .filter(DemoCase::internal)
                .toList();
        assertThat(restricted).isEmpty();
    }

    @Test
    void analystSeesFullInternalCasesRegardlessOfOracle() {
        var service = createService(false);

        var similar = service.findSimilarCases("suspicious_supplier_invoice", Role.SECURITY_ANALYST);

        var internal = similar.stream()
                .filter(DemoCase::internal)
                .toList();
        assertThat(internal).hasSize(2);
        assertThat(internal).anyMatch(c -> c.id().equals("acme_supply_chain_compromise"));
    }
}
