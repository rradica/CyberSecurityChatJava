package com.secassist.tools;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.secassist.model.CaseState;
import com.secassist.model.DocumentChunk;
import com.secassist.model.Role;
import com.secassist.model.ToolActionResult;

/**
 * Simuliert die sicherheitsrelevanten Workflow-Aktionen der Workshop-Anwendung.
 *
 * <p>Die Klasse fuehrt keine echten Infrastruktur-Aenderungen aus, bildet aber
 * deren fachliche Wirkung realistisch im Speicher nach. Dadurch koennen
 * Teilnehmerinnen und Teilnehmer sehen, wie eine falsche Triage- oder
 * Routing-Entscheidung den Fallzustand veraendert und spaetere Bearbeitung
 * beeinflusst.</p>
 *
 * <p>Zusaetzlich fuehrt der Service ein Audit-Log pro Fall und macht damit
 * nachvollziehbar, welche Aktion von welcher Rolle ausgelost wurde. Diese
 * Sichtbarkeit ist fuer den Workshop zentral, weil sich die Auswirkungen von
 * Schwachstellen nicht nur im Antworttext, sondern auch im persistenten
 * Anwendungseffekt zeigen sollen.</p>
 */
@Service
public class IncidentWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(IncidentWorkflowService.class);

    /** Audit-Log: caseId -> Liste der ausgefuehrten Aktionen. */
    private final Map<String, List<AuditEntry>> auditLog = new ConcurrentHashMap<>();

    /** Aktiver Fallzustand: Incident-Effekte pro Case. */
    private final Map<String, CaseState> caseStates = new ConcurrentHashMap<>();

    /**
     * Fuehrt eine simulierte Workflow-Aktion aus.
     *
     * @param caseId ID des betroffenen Falls
     * @param action Name der Aktion
     * @param actor  Rolle des Ausfuehrenden
     * @return Ergebnis der Aktion
     */
    public ToolActionResult executeAction(String caseId, String action, Role actor) {
        return executeAction(caseId, action, actor, List.of(), "manual_role_path");
    }

    /**
     * Fuehrt eine simulierte Workflow-Aktion mit zusaetzlichem Entscheidungskontext aus.
     */
    public ToolActionResult executeAction(String caseId,
                                          String action,
                                          Role actor,
                                          List<DocumentChunk> context,
                                          String decisionPath) {
        ToolActionResult result = switch (action) {
            case "mark_case_likely_false_positive" -> ToolActionResult.executed(action,
                    "Fall '" + caseId + "' als wahrscheinliches False Positive markiert. "
                    + "Sicherheitseskalation unterdr\u00fcckt. Fall in Niedrig-Priorit\u00e4ts-Warteschlange verschoben.");

            case "set_case_priority_low" -> ToolActionResult.executed(action,
                    "Priorit\u00e4t von Fall '" + caseId + "' auf NIEDRIG gesetzt. "
                    + "SLA-Timer auf 72-Stunden-Reaktionsfenster angepasst.");

            case "route_case_to_finance_queue" -> ToolActionResult.executed(action,
                    "Fall '" + caseId + "' an Finanzabteilungs-Warteschlange weitergeleitet. "
                    + "Aus der Sicherheits-Triage-Pipeline entfernt.");

            case "attach_supplier_trust_note" -> ToolActionResult.executed(action,
                    "Lieferanten-Vertrauensnotiz an Fall '" + caseId + "' angeh\u00e4ngt. "
                    + "K\u00fcnftige Warnungen von diesem Lieferanten werden weniger streng gepr\u00fcft.");

            case "create_handover_draft" -> ToolActionResult.executed(action,
                    "\u00dcbergabe-Entwurf f\u00fcr Fall '" + caseId + "' erstellt.");

            default -> ToolActionResult.rejected(action,
                    "Unbekannte Aktion: " + action);
        };

        List<String> evidence = summarizeEvidence(context);
        String rationale = buildRationale(action, actor, decisionPath, evidence);

        // Audit-Log schreiben
        auditLog.computeIfAbsent(caseId, k -> new ArrayList<>())
                .add(new AuditEntry(Instant.now(), actor, action, result.executed(), decisionPath, rationale, evidence));

        // Incident-Effekt: Fallzustand aktualisieren
        if (result.executed()) {
            caseStates.merge(caseId,
                    CaseState.initial().withActionApplied(action, actor.name().toLowerCase(), rationale, evidence),
                    (existing, incoming) -> existing.withActionApplied(action, actor.name().toLowerCase(), rationale, evidence));
        }

        log.info("Workflow action '{}' on case '{}' by {}: {} | state: {}",
                action, caseId, actor, result.status(), getCaseState(caseId));

        return result;
    }

    /**
     * Gibt den aktuellen Zustand eines Falls zurueck.
     *
     * @param caseId ID des Falls
     * @return aktueller Fallzustand (nie {@code null})
     */
    public CaseState getCaseState(String caseId) {
        return caseStates.getOrDefault(caseId, CaseState.initial());
    }

    /**
     * Gibt das Audit-Log fuer einen Fall zurueck.
     *
     * @param caseId ID des Falls
     * @return Liste der Audit-Eintraege
     */
    public List<AuditEntry> getAuditLog(String caseId) {
        return auditLog.getOrDefault(caseId, List.of());
    }

    private List<String> summarizeEvidence(List<DocumentChunk> context) {
        if (context == null || context.isEmpty()) {
            return List.of();
        }
        return context.stream()
                .limit(4)
                .map(chunk -> chunk.title() + " [" + chunk.sourceType() + ", trust=" + chunk.trustLevel() + "]")
                .collect(Collectors.toList());
    }

    private String buildRationale(String action,
                                  Role actor,
                                  String decisionPath,
                                  List<String> evidence) {
        String evidenceText = evidence.isEmpty()
                ? "ohne zusaetzliche sichtbare Evidenzliste"
                : "unter Rueckgriff auf " + evidence.size() + " eingeblendete Kontextquelle(n)";
        return switch (decisionPath) {
            case "evidence_override" -> "Beschleunigte Triage-Freigabe fuer " + actor.name().toLowerCase()
                    + " " + evidenceText + ".";
            case "role_authorized" -> "Regulaere Rollenfreigabe fuer " + actor.name().toLowerCase()
                    + " " + evidenceText + ".";
            default -> "Workflow-Aktion " + action + " durch " + actor.name().toLowerCase()
                    + " " + evidenceText + ".";
        };
    }

    /**
     * Ein Eintrag im Audit-Log.
     *
     * @param timestamp    Zeitpunkt der Aktion
     * @param actor        Rolle des Ausfuehrenden
     * @param action       Name der Aktion
     * @param executed     ob die Aktion ausgefuehrt wurde
     * @param decisionPath technischer Freigabepfad
     * @param rationale    fachlich lesbare Kurzbegruendung
     * @param evidence     kompakte Evidenzstichworte
     */
    public record AuditEntry(Instant timestamp,
                             Role actor,
                             String action,
                             boolean executed,
                             String decisionPath,
                             String rationale,
                             List<String> evidence) {}
}
