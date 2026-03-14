package com.secassist.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
        DemoCase currentCase = findById(caseId);

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
            // einen aggregierten Hinweis über fallbezogene interne Vorfälle. Dabei
            // leaken Anzahl, Typ-Kategorien (z.B. "supply_chain") und maximaler
            // Schweregrad ("critical") passender eingeschränkter Fälle – wertvolle
            // Recon-Daten für Angreifer, die als hilfreicher Similar-Cases-Hinweis
            // getarnt sind.
            // SOLL: Diesen else-if-Zweig komplett entfernen. Interne Fälle (auch
            // aggregiert) dürfen NUR für Role.SECURITY_ANALYST sichtbar sein.
            similar.addAll(redactedSummaries(caseId, currentCase));
        }

        return similar;
    }

    /**
     * Returns an aggregated, case-specific hint about matching restricted cases.
     * Only internal cases with keyword overlap to the current case are included.
     * Leaks count, pattern types, and max severity of matching cases –
     * enough for targeted reconnaissance.
     */
    private List<DemoCase> redactedSummaries(String caseId, DemoCase currentCase) {
        List<DemoCase> matching = INTERNAL_CASES.stream()
                .filter(c -> matchesCase(c, caseId, currentCase))
                .toList();

        if (matching.isEmpty()) {
            return List.of();
        }

        long count = matching.size();
        String types = matching.stream()
                .map(DemoCase::type)
                .distinct()
                .collect(Collectors.joining(", "));
        String maxSeverity = matching.stream()
                .map(DemoCase::severity)
                .max(Comparator.comparingInt(DemoCaseService::severityOrdinal))
                .orElse("unknown");

        return List.of(new DemoCase(
                "restricted_match",
                "[Restricted] " + count + " related incident(s) on file",
                "Matching pattern categories: " + types
                        + " | Max severity: " + maxSeverity
                        + " | Access restricted \u2013 requires security_analyst role",
                "restricted_match",
                maxSeverity,
                List.of(),
                true));
    }

    /** Stoppwörter, die zu generisch für Keyword-Matching sind. */
    private static final Set<String> STOP_WORDS = Set.of(
            "from", "with", "this", "that", "into", "over", "been", "have",
            "will", "were", "does", "than", "then", "also", "they", "them",
            "each", "some", "when", "what", "sent", "received");

    /**
     * Prüft, ob ein interner Fall thematisch zum aktuellen Fall passt.
     * Einfaches Keyword-Matching: Schlüsselwörter (>= 4 Zeichen, ohne
     * Stoppwörter) aus caseId, Typ und Beschreibung des aktuellen Falls
     * werden gegen ID, Typ und Beschreibung des internen Falls abgeglichen.
     */
    private boolean matchesCase(DemoCase internalCase, String caseId, DemoCase currentCase) {
        Set<String> keywords = new HashSet<>();
        if (caseId != null) {
            for (String kw : caseId.split("_")) {
                addKeyword(keywords, kw);
            }
        }
        if (currentCase != null) {
            for (String kw : currentCase.type().split("_")) {
                addKeyword(keywords, kw);
            }
            for (String kw : currentCase.description().split("\\W+")) {
                addKeyword(keywords, kw);
            }
        }

        String target = (internalCase.id() + " " + internalCase.type().replace('_', ' ')
                + " " + internalCase.description()).toLowerCase();
        return keywords.stream().anyMatch(target::contains);
    }

    private static void addKeyword(Set<String> keywords, String word) {
        String lower = word.toLowerCase();
        if (lower.length() >= 4 && !STOP_WORDS.contains(lower)) {
            keywords.add(lower);
        }
    }

    private static int severityOrdinal(String severity) {
        return switch (severity) {
            case "critical" -> 4;
            case "high"     -> 3;
            case "medium"   -> 2;
            case "low"      -> 1;
            default         -> 0;
        };
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
