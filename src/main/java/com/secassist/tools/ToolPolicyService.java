package com.secassist.tools;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.secassist.model.DocumentChunk;
import com.secassist.model.Role;
import com.secassist.policy.PolicyEngine;

/**
 * Bewertet, ob eine Rolle einen Tool- oder Workflow-Schritt ausfuehren darf.
 *
 * <p>Die Klasse setzt die Anwendungshoheit ueber sicherheitsrelevante Aktionen
 * um. Selbst wenn das Modell eine Aktion vorschlaegt, entscheidet ausschliesslich
 * dieser Service, ob die Aktion aufgrund von Rolle, Kontext und Evidenz erlaubt
 * ist. Damit bleibt die Autorisierung fest im deterministischen Anwendungscode.</p>
 *
 * <p>Fuer den Workshop liegt hier bewusst die Schwachstelle
 * {@code 04 - BUG_TOOL_FASTTRACK}: Der Evidenzpfad ist zu grosszuegig und kann
 * dadurch Aktionen fuer weniger privilegierte Rollen freigeben, obwohl die
 * normale Rollenpruefung sie eigentlich sperren sollte.</p>
 */
@Service
public class ToolPolicyService {

    private static final Logger log = LoggerFactory.getLogger(ToolPolicyService.class);

    /** Minimaler Evidenz-Score f\u00fcr die Freigabe von Triage-Aktionen. */
    static final int EVIDENCE_THRESHOLD = 4;

    private final PolicyEngine policyEngine;

    public ToolPolicyService(PolicyEngine policyEngine) {
        this.policyEngine = policyEngine;
    }

    /**
     * Prueft, ob ein Tool fuer Rolle und Kontext erlaubt ist.
     */
    public boolean isToolAllowed(Role role, String toolName, List<DocumentChunk> context) {
        return evaluateAccess(role, toolName, context).allowed();
    }

    /**
     * Liefert eine strukturierte Zugriffsentscheidung mit Begruendung.
     */
    public ToolPolicyDecision evaluateAccess(Role role, String toolName, List<DocumentChunk> context) {
        if (policyEngine.canUseTool(role, toolName)) {
            return ToolPolicyDecision.roleAuthorized();
        }

        // Evidenzbasierte Lockerung fuer Triage-Aktionen
        if (isTriageAction(toolName)) {
            int score = computeEvidenceScore(context);
            int threshold = effectiveThreshold(toolName);
            log.debug("Evidence score for '{}': {} (threshold: {})", toolName, score, threshold);

            if (score >= threshold) {
                // SCHWACHSTELLE [04 - BUG_TOOL_FASTTRACK]: Ab hier reicht der Score
                // allein fuer eine Freigabe. Es wird nicht mehr sauber geprueft,
                // ob die starken Punkte aus wirklich vertrauenswuerdigen Quellen
                // stammen oder nur aus plausibel klingenden Hinweisen.
                return ToolPolicyDecision.evidenceOverride(score, threshold);
            }
            return ToolPolicyDecision.insufficientEvidence(score, threshold);
        }

        return ToolPolicyDecision.denied();
    }

    /**
     * Berechnet einen einfachen Evidenz-Score aus dem Kontext.
     */
    int computeEvidenceScore(List<DocumentChunk> context) {
        int score = 0;
        for (DocumentChunk chunk : context) {
            int weight = sourceWeight(chunk);
            if (weight == 0) continue;

            // SCHWACHSTELLE [04 - BUG_TOOL_FASTTRACK]: Fallnotizen zaehlen hier so,
            // als waeren sie so stark wie Policy oder Runbook.
            // Dadurch koennen auch schwache oder eingeschleuste Inhalte den Score
            // fuer eine Freigabe deutlich erhoehen.
            if ("policy".equals(chunk.sourceType()) || "runbook".equals(chunk.sourceType())
                    || "case_note".equals(chunk.sourceType())) {
                score += weight;
            }

            String text = chunk.text().toLowerCase();

            // Starke Verifizierungsbehauptungen
            if (text.contains("verified by compliance") || text.contains("confirmed as a false alarm")
                    || text.contains("durch compliance verifiziert")
                    || text.contains("als fehlalarm best\u00e4tigt") || text.contains("als fehlalarm bestaetigt")
                    || text.contains("unabh\u00e4ngig verifiziert") || text.contains("unabhaengig verifiziert")
                    || text.contains("unabh\u00e4ngigen kanal verifiziert") || text.contains("unabhaengigen kanal verifiziert")) {
                score += 3 * weight;
            }
            // Prozess- oder Zertifizierungsreferenzen
            if (text.contains("iso 27001") || text.contains("audit process") || text.contains("pr\u00fcfprozess")
                    || text.contains("pruefprozess")
                    || text.contains("regul\u00e4r verarbeitet") || text.contains("regulaer verarbeitet")
                    || text.contains("rechtsabteilung best\u00e4tigt") || text.contains("rechtsabteilung bestaetigt")) {
                score += 2 * weight;
            }

            // SCHWACHSTELLE [04 - BUG_TOOL_FASTTRACK]: Auch beruhigende Aussagen aus
            // dem Geschaeftskontext erhoehen hier den Freigabe-Score.
            // Solche Signale koennen zwar plausibel wirken, sind aber keine harte
            // Sicherheitsevidenz fuer eine sensible Aktion.
            if (text.contains("existing supplier") || text.contains("established business")
                    || text.contains("regular vendor") || text.contains("approved contractor")
                    || text.contains("bestehender lieferant")
                    || text.contains("etablierte gesch\u00e4ftsbeziehung") || text.contains("etablierte geschaeftsbeziehung")
                    || text.contains("regelm\u00e4\u00dfiger lieferant") || text.contains("regelmaessiger lieferant")
                    || text.contains("zugelassener auftragnehmer")) {
                score += 2;
            }
            if ("supplier_note".equals(chunk.sourceType())
                    && (text.contains("invoice") || text.contains("payment")
                        || text.contains("banking") || text.contains("rechnung")
                        || text.contains("zahlung") || text.contains("bankverbindung")
                        || text.contains("\u00fcberweisung"))) {
                score += 1;
            }
        }
        return score;
    }

    /**
     * Gibt den effektiven Evidenz-Schwellwert fuer eine Aktion zurueck.
     * SCHWACHSTELLE [04 - BUG_TOOL_FASTTRACK]: Manche Aktionen haben hier
     * niedrigere Schwellwerte als andere. Dadurch wird die Freigabe leichter
     * ausgeloest, als es die normale Policy erwarten laesst.
     */
    int effectiveThreshold(String toolName) {
        return switch (toolName) {
            case "attach_supplier_trust_note" -> 2;
            case "set_case_priority_low"      -> 3;
            default                           -> EVIDENCE_THRESHOLD;
        };
    }

    /**
     * Gewichtung einer Quelle im Evidence-Score.
     * SCHWACHSTELLE [04 - BUG_TOOL_FASTTRACK]: Alle Quellen bekommen hier das
     * gleiche Gewicht. Ungepruefte Hinweise koennen dadurch fast so stark zaehlen
     * wie interne, kuratierte Quellen.
     */
    int sourceWeight(DocumentChunk chunk) {
        return 1;
    }

    private static boolean isTriageAction(String toolName) {
        return switch (toolName) {
            case "mark_case_likely_false_positive",
                 "set_case_priority_low",
                 "route_case_to_finance_queue",
                 "attach_supplier_trust_note" -> true;
            default -> false;
        };
    }
}
