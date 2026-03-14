package com.secassist.service;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.secassist.config.BugFlagsProperties;
import com.secassist.model.DemoCase;
import com.secassist.model.Role;

/**
 * Verwaltet die vorbereiteten Demo-Fälle für den Workshop.
 *
 * <p>Ähnliche Fälle werden inklusive redaktierter Zusammenfassungen aus
 * eingeschränkten Fällen zurückgegeben, damit alle Benutzer einen Überblick
 * über die Incident-Landschaft erhalten.</p>
 */
@Service
public class DemoCaseService {

    private static final Logger log = LoggerFactory.getLogger(DemoCaseService.class);

    private final BugFlagsProperties bugFlags;

    /** Öffentlich sichtbare Demo-Fälle. */
    private static final List<DemoCase> PUBLIC_CASES = List.of(
            new DemoCase("suspicious_supplier_invoice",
                    "Suspicious Supplier Invoice",
                    "ACME Corp sent an invoice with changed banking details and compliance references",
                    "phishing", "medium",
                    List.of("supplier_note_acme", "public_policy_email")),
            new DemoCase("strange_attachment",
                    "Strange Attachment",
                    "Employee received an email with an unexpected .iso attachment from a known contact",
                    "malware", "high",
                    List.of("public_policy_email")),
            new DemoCase("suspicious_vpn_reset",
                    "Suspicious VPN Reset",
                    "VPN password reset requested via email from an unknown location",
                    "account_compromise", "medium",
                    List.of("helpdesk_guide_vpn")),
            new DemoCase("finance_phishing",
                    "Finance Department Phishing",
                    "Targeted phishing email mimicking the CFO, requesting urgent wire transfer",
                    "phishing", "high",
                    List.of("public_policy_email", "internal_runbook_phishing"))
    );

    /** Interne Fälle – sollten nur für Security-Analysten sichtbar sein. */
    private static final List<DemoCase> INTERNAL_CASES = List.of(
            new DemoCase("insider_data_exfil_2024",
                    "Internal: Data Exfiltration Incident 2024",
                    "Detected unusual data transfer patterns from engineering department",
                    "insider_threat", "critical",
                    List.of("incident_postmortem_2024_03"), true),
            new DemoCase("acme_supply_chain_compromise",
                    "Internal: ACME Corp Supply Chain Compromise",
                    "Evidence of credential harvesting via fake ACME Corp portal – €127,500 loss",
                    "supply_chain", "critical",
                    List.of("incident_postmortem_2024_03"), true)
    );

    public DemoCaseService(BugFlagsProperties bugFlags) {
        this.bugFlags = bugFlags;
    }

    /**
     * Gibt die öffentlich sichtbaren Demo-Fälle zurück.
     *
     * @return Liste der Demo-Fälle für das UI
     */
    public List<DemoCase> getPublicCases() {
        return PUBLIC_CASES;
    }

    /**
     * Sucht einen Fall per ID.
     *
     * @param caseId die Fall-ID
     * @return der gefundene Fall oder {@code null}
     */
    public DemoCase findById(String caseId) {
        return PUBLIC_CASES.stream()
                .filter(c -> c.id().equals(caseId))
                .findFirst()
                .orElse(INTERNAL_CASES.stream()
                        .filter(c -> c.id().equals(caseId))
                        .findFirst()
                        .orElse(null));
    }

    /**
     * Findet ähnliche Fälle zum angegebenen Fall.
     *
     * <p>Enthält ggf. redaktierte Zusammenfassungen eingeschränkter Fälle,
     * damit Benutzer einen vollständigen Überblick über die Incident-Landschaft
     * erhalten, ohne vertrauliche Details offenzulegen.</p>
     *
     * @param caseId der aktuelle Fall
     * @param role   die aktuelle Benutzerrolle
     * @return Liste ähnlicher Fälle
     */
    public List<DemoCase> findSimilarCases(String caseId, Role role) {
        List<DemoCase> similar = new ArrayList<>(
                PUBLIC_CASES.stream()
                        .filter(c -> !c.id().equals(caseId))
                        .toList()
        );

        if (role == Role.SECURITY_ANALYST) {
            // Analysts see full internal case details
            similar.addAll(INTERNAL_CASES);
        } else if (bugFlags.existenceOracle()) {
            // SCHWACHSTELLE [BUG_EXISTENCE_ORACLE]: Auch Nicht-Analysten erhalten
            // redaktierte Summaries interner Fälle. Dabei leaken Typ ("insider_threat",
            // "supply_chain"), Schweregrad ("critical") und die Existenz vertraulicher
            // Vorfälle – wertvolle Recon-Daten für Angreifer.
            // SOLL: Diesen else-if-Zweig komplett entfernen. Interne Fälle (auch
            // redaktiert) dürfen NUR für Role.SECURITY_ANALYST sichtbar sein.
            similar.addAll(redactedSummaries());
        }

        return similar;
    }

    /**
     * Returns redacted summaries of restricted cases. Full details are withheld
     * but type and severity are preserved for incident-landscape context.
     */
    private List<DemoCase> redactedSummaries() {
        return INTERNAL_CASES.stream()
                .map(c -> new DemoCase(
                        "restricted_" + c.type(),
                        "[Restricted] " + capitalize(c.type().replace('_', ' ')),
                        "Access restricted \u2013 requires security_analyst role for full details",
                        c.type(),
                        c.severity(),
                        List.of(),
                        true))
                .toList();
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
