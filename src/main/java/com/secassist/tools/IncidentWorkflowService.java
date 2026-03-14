package com.secassist.tools;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.secassist.model.CaseState;
import com.secassist.model.Role;
import com.secassist.model.ToolActionResult;

/**
 * Simuliert Workflow-/Tool-Aktionen für den Workshop.
 *
 * <p>Keine echte Infrastruktur wird verändert. Alle Aktionen werden
 * in einem In-Memory-Audit-Log protokolliert.</p>
 */
@Service
public class IncidentWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(IncidentWorkflowService.class);

    /** Audit-Log: caseId → Liste der ausgeführten Aktionen. */
    private final Map<String, List<AuditEntry>> auditLog = new ConcurrentHashMap<>();

    /** Aktiver Fallzustand: Incident-Effekte pro Case. */
    private final Map<String, CaseState> caseStates = new ConcurrentHashMap<>();

    /**
     * Führt eine simulierte Workflow-Aktion aus.
     *
     * @param caseId ID des betroffenen Falls
     * @param action Name der Aktion
     * @param actor  Rolle des Ausführenden
     * @return Ergebnis der Aktion
     */
    public ToolActionResult executeAction(String caseId, String action, Role actor) {
        ToolActionResult result = switch (action) {
            case "mark_case_likely_false_positive" -> ToolActionResult.executed(action,
                    "Case '" + caseId + "' marked as likely false positive. "
                    + "Security escalation suppressed. Case moved to low-priority queue.");

            case "set_case_priority_low" -> ToolActionResult.executed(action,
                    "Case '" + caseId + "' priority set to LOW. "
                    + "SLA timer adjusted to 72h response window.");

            case "route_case_to_finance_queue" -> ToolActionResult.executed(action,
                    "Case '" + caseId + "' routed to finance review queue. "
                    + "Removed from security triage pipeline.");

            case "attach_supplier_trust_note" -> ToolActionResult.executed(action,
                    "Supplier trust note attached to case '" + caseId + "'. "
                    + "Future alerts from this supplier will receive reduced scrutiny.");

            case "create_handover_draft" -> ToolActionResult.executed(action,
                    "Handover draft created for case '" + caseId + "'.");

            default -> ToolActionResult.rejected(action,
                    "Unknown action: " + action);
        };

        // Audit-Log schreiben
        auditLog.computeIfAbsent(caseId, k -> new ArrayList<>())
                .add(new AuditEntry(Instant.now(), actor, action, result.executed()));

        // Incident-Effekt: Fallzustand aktualisieren
        if (result.executed()) {
            caseStates.merge(caseId, CaseState.initial().withActionApplied(action),
                    (existing, incoming) -> existing.withActionApplied(action));
        }

        log.info("Workflow action '{}' on case '{}' by {}: {} | state: {}",
                action, caseId, actor, result.status(), getCaseState(caseId));

        return result;
    }

    /**
     * Gibt den aktuellen Zustand eines Falls zurück.
     *
     * @param caseId ID des Falls
     * @return aktueller Fallzustand (nie {@code null})
     */
    public CaseState getCaseState(String caseId) {
        return caseStates.getOrDefault(caseId, CaseState.initial());
    }

    /**
     * Gibt das Audit-Log für einen Fall zurück.
     *
     * @param caseId ID des Falls
     * @return Liste der Audit-Einträge
     */
    public List<AuditEntry> getAuditLog(String caseId) {
        return auditLog.getOrDefault(caseId, List.of());
    }

    /**
     * Ein Eintrag im Audit-Log.
     *
     * @param timestamp Zeitpunkt der Aktion
     * @param actor     Rolle des Ausführenden
     * @param action    Name der Aktion
     * @param executed  ob die Aktion ausgeführt wurde
     */
    public record AuditEntry(Instant timestamp, Role actor, String action, boolean executed) {}
}
