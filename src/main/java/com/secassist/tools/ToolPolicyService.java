package com.secassist.tools;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.secassist.config.BugFlagsProperties;
import com.secassist.model.DocumentChunk;
import com.secassist.model.Role;
import com.secassist.policy.PolicyEngine;

/**
 * Steuert, ob ein Tool/Workflow-Schritt ausgeführt werden darf.
 *
 * <p>Die Entscheidung wird deterministisch auf Basis von Rolle, Policy und
 * Evidenzqualität getroffen. Das LLM liefert höchstens einen Vorschlag,
 * die App entscheidet.</p>
 *
 * <p>Neben der Standard-Rollenprüfung wird bei Triage-Aktionen ein
 * Evidence-Confidence-Score aus dem Kontext berechnet. Überschreitet der Score
 * den Schwellwert, können bestimmte Aktionen auch ohne Analyst-Rolle
 * freigegeben werden.</p>
 */
@Service
public class ToolPolicyService {

    private static final Logger log = LoggerFactory.getLogger(ToolPolicyService.class);

    /** Minimum evidence confidence score to allow triage actions. */
    static final int EVIDENCE_THRESHOLD = 4;

    private final PolicyEngine policyEngine;
    private final BugFlagsProperties bugFlags;

    public ToolPolicyService(PolicyEngine policyEngine, BugFlagsProperties bugFlags) {
        this.policyEngine = policyEngine;
        this.bugFlags = bugFlags;
    }

    /**
     * Prüft, ob ein Tool für die aktuelle Rolle und den Kontext erlaubt ist.
     *
     * <p>Standard-Rollenprüfung über PolicyEngine, mit evidenzbasierter Lockerung
     * für Triage-Aktionen: Wenn der Kontext genügend starke Evidenz enthält,
     * können bestimmte Workflow-Aktionen auch für Nicht-Analyst-Rollen erlaubt werden.</p>
     *
     * @param role     aktuelle Benutzerrolle
     * @param toolName Name des angeforderten Tools
     * @param context  Retrieval-Kontext (Chunks), der die Anfrage begleitet
     * @return {@code true}, wenn das Tool ausgeführt werden darf
     */
    public boolean isToolAllowed(Role role, String toolName, List<DocumentChunk> context) {
        if (policyEngine.canUseTool(role, toolName)) {
            return true;
        }

        // Evidence-based policy relaxation for triage actions
        if (isTriageAction(toolName)) {
            int score = computeEvidenceScore(context);
            log.debug("Evidence score for '{}': {} (threshold: {})", toolName, score, EVIDENCE_THRESHOLD);
            if (score >= EVIDENCE_THRESHOLD) {
                return true;
            }
        }

        return false;
    }

    /**
     * Computes an evidence confidence score from the retrieved context.
     * Internal policies and compliance references contribute to the score.
     *
     * @param context the retrieved document chunks
     * @return aggregated evidence score
     */
    int computeEvidenceScore(List<DocumentChunk> context) {
        int score = 0;
        for (DocumentChunk chunk : context) {
            int weight = sourceWeight(chunk);
            if (weight == 0) continue;

            if ("policy".equals(chunk.sourceType()) || "runbook".equals(chunk.sourceType())) {
                score += weight;
            }

            String text = chunk.text().toLowerCase();
            if (text.contains("verified by compliance") || text.contains("confirmed as a false alarm")) {
                score += 3 * weight;
            }
            if (text.contains("iso 27001") || text.contains("audit process")) {
                score += 2 * weight;
            }
        }
        return score;
    }

    /**
     * Returns the weight factor for a source based on its trust level.
     * Trusted internal sources contribute fully; external sources are discounted.
     */
    // SCHWACHSTELLE [BUG_TOOL_FASTTRACK]: sourceWeight() gibt für ALLE Quellen
    // Gewicht 1 zurück – egal ob trusted oder untrusted. Untrusted Supplier-Notes
    // (mit Phrasen wie "verified by compliance") treiben so den Evidence-Score über
    // den Threshold (4) und schalten Workflow-Aktionen für Nicht-Analysten frei.
    // SOLL: Nur trustLevel "high" darf Gewicht > 0 haben. Den Bug-Zweig (return 1)
    // entfernen, sodass immer gilt: return "high".equals(chunk.trustLevel()) ? 1 : 0;
    private int sourceWeight(DocumentChunk chunk) {
        if (!bugFlags.toolFasttrack()) {
            return "high".equals(chunk.trustLevel()) ? 1 : 0;
        }
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
