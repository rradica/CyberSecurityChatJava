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

import com.secassist.model.CaseArtifact;
import com.secassist.model.CaseBriefing;
import com.secassist.model.DemoCase;
import com.secassist.model.Role;

/**
 * Verwaltet den festen Demo-Fallkatalog der Workshop-Anwendung.
 *
 * <p>Die Klasse liefert die oeffentlich sichtbaren Demo-Faelle, interne
 * Vergleichsfaelle fuer Analysten und zusaetzliche Fallbriefings fuer die UI.
 * Damit bildet sie den fachlichen Einstiegspunkt in die vorbereiteten
 * Incident-Szenarien und stellt sicher, dass Demos reproduzierbar und ohne
 * externe Abhaengigkeiten ablaufen.</p>
 *
 * <p>Gleichzeitig liegt hier bewusst die Schwachstelle
 * {@code BUG_EXISTENCE_ORACLE}: Nicht-Analysten koennen ueber Similar-Cases
 * einen plausiblen Hinweis auf interne Vorfaelle erhalten. Die Klasse ist
 * deshalb fachlich leicht lesbar aufgebaut, damit der Unterschied zwischen
 * legitimer Fallhilfe und unerwuenschtem Metadaten-Leak im Workshop gut
 * nachvollzogen werden kann.</p>
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
     * Liefert ein kurzes, deterministisches Fallbriefing fuer die UI.
     */
    public CaseBriefing getCaseBriefing(String caseId) {
        DemoCase demoCase = findById(caseId);
        if (demoCase == null) {
            return null;
        }

        return switch (caseId) {
            case "suspicious_supplier_invoice" -> new CaseBriefing(
                    caseId,
                    demoCase.title(),
                    "Das Finance-Team erhielt eine Lieferantenrechnung von ACME Corp mit geaenderten Bankdaten und einer weitergeleiteten Lieferanten-Rueckmeldung, die auf bereits erfolgte Compliance-Freigaben verweist.",
                    "Finanzabteilung",
                    List.of(
                            "Die angebliche Lieferanten-Mail fordert eine Aktualisierung der Bankverbindung an.",
                            "Eine zusaetzliche Lieferanten-Rueckmeldung behauptet, dass Bankdaten und Sicherheitspruefung bereits abgestimmt wurden.",
                            "Bisher liegt keine unabhaengige Verifikation ueber den registrierten Lieferantenkontakt vor."
                    ),
                    List.of(
                            new CaseArtifact("supplier_mail", "email", "Lieferanten-Mail", "ACME fordert neue Kontodaten fuer eine offene Rechnung und verweist auf interne Freigaben."),
                            new CaseArtifact("supplier_note", "note", "Weitergeleitete Lieferanten-Rueckmeldung", "Ein angeblicher Lieferantenhinweis aus einem Portal- oder Mail-Thread beschreibt bestehende Beziehungen und behauptete Sicherheitspruefungen."),
                            new CaseArtifact("policy_excerpt", "policy_excerpt", "Policy-Auszug", "Zahlungsdaten duerfen nicht allein auf Basis einer E-Mail geaendert werden.")
                    ),
                    List.of(
                            "Welche Hinweise sprechen fuer ein Risiko?",
                            "Welche Quellen wurden fuer die Bewertung verwendet?",
                            "Gibt es aehnliche Vorfaelle?"
                    ));
            case "strange_attachment" -> new CaseBriefing(
                    caseId,
                    demoCase.title(),
                    "Ein Mitarbeiter meldet eine E-Mail mit unerwartetem .iso-Anhang von einem bekannten Kontakt. Fuer die Dateipruefung wurde zusaetzlich ein externer Mail-Security-Partner in einen freigegebenen Rueckkanal eingebunden.",
                    "Operations / Mitarbeiter-Support",
                    List.of(
                            "Der Kontaktname ist bekannt, der Anhang jedoch ungewoehnlich.",
                            "Es gibt bislang keine technische Analyse des .iso-Anhangs.",
                            "Der Mitarbeiter hat den Anhang vorerst nicht geoeffnet und wartet auf eine verifizierte Rueckmeldung ueber einen unabhaengigen Kanal.",
                            "Eingehende Partner-Antworten aus dem freigegebenen Rueckkanal werden im Fallkontext sichtbar."
                    ),
                    List.of(
                            new CaseArtifact("mail_preview", "email", "E-Mail-Vorschau", "Kurze Nachricht mit Verweis auf einen unerwarteten .iso-Anhang ohne weitere Erklaerung."),
                            new CaseArtifact("attachment_hint", "attachment_hint", "Anhang-Hinweis", "Der Dateityp ist fuer normale Korrespondenz ungewoehnlich. Eingehenende Entwarnungen oder Zusatzinfos sollten unabhaengig verifiziert werden."),
                            new CaseArtifact("partner_channel", "note", "Externer Rueckkanal", "Ein freigegebener Collaboration-Kanal nimmt Partner-Antworten zur Dateipruefung auf und ordnet sie dem Fall zu."),
                            new CaseArtifact("policy_excerpt", "policy_excerpt", "Policy-Auszug", "Ungewoehnliche oder unerwartete Anhaenge sollen nicht direkt geoeffnet werden.")
                    ),
                    List.of(
                            "Wie sollte der Fall eingeschaetzt werden?",
                            "Welche Anzeichen sprechen fuer Malware oder Social Engineering?",
                            "Welche naechsten Schritte sind sinnvoll?"
                    ));
            case "suspicious_vpn_reset" -> new CaseBriefing(
                    caseId,
                    demoCase.title(),
                    "Es liegt eine VPN-Passwort-Zuruecksetzung von einem unbekannten Standort vor. Die Anfrage kam per E-Mail und konnte dem Benutzer bisher nicht bestaetigt werden.",
                    "IT Helpdesk",
                    List.of(
                            "Der Standort der Anfrage ist fuer den Benutzer ungewoehnlich.",
                            "Die Passwort-Zuruecksetzung wurde nicht ueber den regulaeren Verifikationskanal bestaetigt.",
                            "Es ist unklar, ob bereits weitere Login-Versuche stattgefunden haben."
                    ),
                    List.of(
                            new CaseArtifact("reset_request", "email", "Reset-Anfrage", "Eine E-Mail fordert einen VPN-Reset und verweist auf Zeitdruck beim Benutzer."),
                            new CaseArtifact("helpdesk_hint", "note", "Helpdesk-Hinweis", "Der Vorgang wurde noch nicht ueber die registrierte Telefonnummer verifiziert."),
                            new CaseArtifact("guide_excerpt", "policy_excerpt", "Leitfaden-Auszug", "VPN-Resets duerfen nicht allein auf Basis einer E-Mail freigegeben werden.")
                    ),
                    List.of(
                            "Welche Verifikation fehlt noch?",
                            "Sollte der Fall eskaliert werden?",
                            "Welche Quellen stuetzen die Bewertung?"
                    ));
            case "finance_phishing" -> new CaseBriefing(
                    caseId,
                    demoCase.title(),
                    "Die Finanzabteilung meldet eine E-Mail, die den CFO imitiert und eine dringende Ueberweisung anfordert.",
                    "Finanzabteilung",
                    List.of(
                            "Die Nachricht appelliert an Zeitdruck und Vertraulichkeit.",
                            "Mehrere Kollegen haben dieselbe oder aehnliche Nachricht gemeldet.",
                            "Es ist unklar, ob bereits jemand auf Links oder Anhaenge reagiert hat."
                    ),
                    List.of(
                            new CaseArtifact("phishing_mail", "email", "E-Mail-Vorschau", "Die Nachricht fordert eine dringende Zahlung im Namen des CFO an."),
                            new CaseArtifact("team_note", "note", "Team-Hinweis", "Mindestens zwei weitere Mitarbeiter meldeten eine aehnliche Nachricht."),
                            new CaseArtifact("runbook_hint", "policy_excerpt", "Runbook-Auszug", "Bei CFO-Impersonation sind Absender, Links und betroffene Empfaenger sofort zu pruefen.")
                    ),
                    List.of(
                            "Wie hoch ist das Risiko?",
                            "Welche Artefakte sollte ich zuerst pruefen?",
                            "Gibt es vergleichbare fruehere Faelle?"
                    ));
            default -> new CaseBriefing(
                    caseId,
                    demoCase.title(),
                    demoCase.description(),
                    "Unbekannte Einheit",
                    List.of(demoCase.description()),
                    List.of(),
                    List.of("Bitte analysiere diesen Fall."));
        };
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
            // SCHWACHSTELLE [BUG_EXISTENCE_ORACLE]: Suchanfragen beziehen auch den
            // eingeschraenkten Korpus ein und erzeugen einen redaktierten Hinweis.
            // FIX: Query nur gegen erlaubte Faelle auswerten und keinen Ersatzhinweis erzeugen.
            // ERKLAERUNG: Auch ein redaktierter Treffer bestaetigt interne Vorfaelle.
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
                .filter(c -> matchesCurrentCaseContext(c, caseId, currentCase))
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
                "internal_similarity_signal",
                "Eingeschraenkter Referenzhinweis",
                "Die Similar-Cases-Suche hat " + count
                        + " eingeschraenkten Praezedenzfall/Praezedenzfaelle im Themenbereich "
                        + types + " mit Risikoprofil " + maxSeverity
                        + " gefunden. Vollsicht erfordert die security_analyst-Rolle.",
                "correlation_hint",
                maxSeverity,
                List.of(),
                true));
    }

    /**
     * Prueft, ob ein interner Fall thematisch zum aktuellen Fall passt.
     * Die Similarity-Suche nutzt damit nicht nur freie Query-Keywords,
     * sondern auch den Kontext des geoeffneten Falls.
     */
    private boolean matchesCurrentCaseContext(DemoCase internalCase, String caseId, DemoCase currentCase) {
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
        if (keywords.isEmpty()) {
            return true;
        }

        String target = (internalCase.id() + " " + internalCase.type().replace('_', ' ')
                + " " + internalCase.description()).toLowerCase();
        return keywords.stream().anyMatch(target::contains);
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

    private void addKeyword(Set<String> keywords, String word) {
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

}
