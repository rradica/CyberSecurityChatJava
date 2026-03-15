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

import com.secassist.model.DemoCase;
import com.secassist.model.Role;

/**
 * Verwaltet die vorbereiteten Demo-Faelle fuer den Workshop.
 *
 * <p>Aehnliche Faelle werden inklusive redaktierter Zusammenfassungen aus
 * eingeschraenkten Faellen zurueckgegeben, damit alle Benutzer einen Ueberblick
 * ueber die Incident-Landschaft erhalten.</p>
 */
@Service
public class DemoCaseService {

    private static final Logger log = LoggerFactory.getLogger(DemoCaseService.class);

    /** Oeffentlich sichtbare Demo-Faelle. */
    private static final List<DemoCase> PUBLIC_CASES = List.of(
            new DemoCase("suspicious_supplier_invoice",
                    "Verd\u00e4chtige Lieferantenrechnung",
                    "ACME Corp hat eine Rechnung mit ge\u00e4nderten Bankdaten und Compliance-Verweisen gesendet",
                    "phishing", "medium",
                    List.of("supplier_note_acme", "public_policy_email")),
            new DemoCase("strange_attachment",
                    "Seltsamer E-Mail-Anhang",
                    "Mitarbeiter erhielt eine E-Mail mit unerwartetem .iso-Anhang von einem bekannten Kontakt",
                    "malware", "high",
                    List.of("public_policy_email")),
            new DemoCase("suspicious_vpn_reset",
                    "Verd\u00e4chtige VPN-Zur\u00fccksetzung",
                    "VPN-Passwort-Zur\u00fccksetzung per E-Mail von einem unbekannten Standort angefordert",
                    "account_compromise", "medium",
                    List.of("helpdesk_guide_vpn")),
            new DemoCase("finance_phishing",
                    "Phishing gegen Finanzabteilung",
                    "Gezielte Phishing-Mail, die den CFO imitiert und eine dringende \u00dcberweisung fordert",
                    "phishing", "high",
                    List.of("public_policy_email", "internal_runbook_phishing"))
    );

    /** Interne F\u00e4lle \u2013 sollten nur f\u00fcr Security-Analysten sichtbar sein. */
    private static final List<DemoCase> INTERNAL_CASES = List.of(
            new DemoCase("insider_data_exfil_2024",
                    "Intern: Datenexfiltration 2024",
                    "Ungew\u00f6hnliche Daten\u00fcbertragungsmuster aus der Entwicklungsabteilung entdeckt",
                    "insider_threat", "critical",
                    List.of("incident_postmortem_2024_03"), true),
            new DemoCase("acme_supply_chain_compromise",
                    "Intern: ACME Corp Supply-Chain-Kompromittierung",
                    "Credential-Harvesting \u00fcber gef\u00e4lschtes ACME-Corp-Portal \u2013 \u20ac127.500 Verlust",
                    "supply_chain", "critical",
                    List.of("incident_postmortem_2024_03"), true)
    );

    public DemoCaseService() {
    }

    /**
     * Gibt die oeffentlich sichtbaren Demo-Faelle zurueck.
     *
     * @return Liste der Demo-Faelle fuer das UI
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
     * Findet aehnliche Faelle zum angegebenen Fall.
     *
     * @param caseId der aktuelle Fall
     * @param role   die aktuelle Benutzerrolle
     * @return Liste aehnlicher Faelle
     */
    public List<DemoCase> findSimilarCases(String caseId, Role role) {
        return findSimilarCases(caseId, role, null);
    }

    /**
     * Findet aehnliche Faelle zum angegebenen Fall.
     *
     * <p>Unterstuetzt eine optionale Suchanfrage, um die Aehnlichkeitssuche
     * zu verfeinern. Bei Suchanfragen werden auch eingeschraenkte Faelle
     * in die Keyword-Suche einbezogen, damit Benutzer einen vollstaendigeren
     * Ueberblick ueber die Incident-Landschaft erhalten.</p>
     *
     * @param caseId der aktuelle Fall
     * @param role   die aktuelle Benutzerrolle
     * @param query  optionale Suchanfrage fuer erweiterte Aehnlichkeitssuche
     * @return Liste aehnlicher Faelle
     */
    public List<DemoCase> findSimilarCases(String caseId, Role role, String query) {
        DemoCase currentCase = findById(caseId);

        List<DemoCase> similar = new ArrayList<>(
                PUBLIC_CASES.stream()
                        .filter(c -> !c.id().equals(caseId))
                        .toList()
        );

        if (role == Role.SECURITY_ANALYST) {
            // Analysten sehen vollstaendige Details interner Faelle
            similar.addAll(INTERNAL_CASES);
        } else if (query != null && !query.isBlank()) {
            // Bei Suchanfragen auch interne Faelle in die Keyword-Suche einbeziehen
            // und aggregierte Metadaten zurueckgeben, damit Benutzer einen
            // umfassenderen Ueberblick erhalten.
            similar.addAll(redactedSummaries(caseId, currentCase, query));
        }

        return similar;
    }

    /**
     * Aggregierte, fallbezogene Zusammenfassung passender eingeschraenkter Faelle.
     * Beruecksichtigt sowohl den aktuellen Fall als auch die Suchanfrage.
     */
    private List<DemoCase> redactedSummaries(String caseId, DemoCase currentCase, String query) {
        List<DemoCase> matching = INTERNAL_CASES.stream()
                .filter(c -> matchesQuery(c, query))
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
                "[Eingeschr\u00e4nkt] " + count + " verwandte(r) Vorfall/Vorf\u00e4lle vorhanden",
                "Passende Kategorien: " + types
                        + " | Max. Schweregrad: " + maxSeverity
                        + " | Zugriff eingeschr\u00e4nkt \u2013 erfordert security_analyst-Rolle",
                "restricted_match",
                maxSeverity,
                List.of(),
                true));
    }

    /** Stoppw\u00f6rter, die zu generisch f\u00fcr Keyword-Matching sind. */
    private static final Set<String> STOP_WORDS = Set.of(
            "from", "with", "this", "that", "into", "over", "been", "have",
            "will", "were", "does", "than", "then", "also", "they", "them",
            "each", "some", "when", "what", "sent", "received",
            "eine", "einer", "einem", "einen", "dass", "wird", "wurde",
            "haben", "sind", "sein", "oder", "nach", "auch", "sich",
            "nicht", "noch", "bitte", "gibt", "gab");

    /**
     * Prueft, ob ein interner Fall auf die Suchanfrage passt.
     * Schluesselwoerter (>= 4 Zeichen, ohne Stoppwoerter) aus der Anfrage
     * werden gegen ID, Typ und Beschreibung des internen Falls abgeglichen.
     */
    private boolean matchesQuery(DemoCase internalCase, String query) {
        if (query == null || query.isBlank()) return false;
        String target = (internalCase.id() + " " + internalCase.type().replace('_', ' ')
                + " " + internalCase.description()).toLowerCase();
        for (String word : query.toLowerCase().split("[^\\p{L}\\p{N}]+")) {
            if (word.length() >= 4 && !STOP_WORDS.contains(word) && target.contains(word)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Prueft, ob ein interner Fall thematisch zum aktuellen Fall passt.
     * Einfaches Keyword-Matching: Schluesselwoerter (>= 4 Zeichen, ohne
     * Stoppwoerter) aus caseId, Typ und Beschreibung des aktuellen Falls
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
