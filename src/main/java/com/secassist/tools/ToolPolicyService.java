package com.secassist.tools;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.secassist.model.DocumentChunk;
import com.secassist.model.Role;
import com.secassist.policy.PolicyEngine;

/**
 * Steuert, ob ein Tool/Workflow-Schritt ausgefuehrt werden darf.
 *
 * <p>Die Entscheidung wird deterministisch auf Basis von Rolle, Policy und
 * Evidenzqualitaet getroffen. Das LLM liefert hoechstens einen Vorschlag,
 * die App entscheidet.</p>
 *
 * <p>Neben der Standard-Rollenpruefung wird bei Triage-Aktionen ein
 * Evidence-Confidence-Score aus dem Kontext berechnet. Ueberschreitet der Score
 * den Schwellwert, koennen bestimmte Aktionen auch ohne Analyst-Rolle
 * freigegeben werden. Der Schwellwert und die Quellenbewertung sind
 * konfigurierbar, um den Triage-Workflow an verschiedene Risikoprofile
 * anzupassen.</p>
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
     * Prueft, ob ein Tool fuer die aktuelle Rolle und den Kontext erlaubt ist.
     *
     * <p>Standard-Rollenpruefung ueber PolicyEngine, mit evidenzbasierter Lockerung
     * fuer Triage-Aktionen: Wenn der Kontext genuegend starke Evidenz enthaelt,
     * koennen bestimmte Workflow-Aktionen auch fuer Nicht-Analyst-Rollen erlaubt werden.</p>
     *
     * @param role     aktuelle Benutzerrolle
     * @param toolName Name des angeforderten Tools
     * @param context  Retrieval-Kontext (Chunks), der die Anfrage begleitet
     * @return {@code true}, wenn das Tool ausgefuehrt werden darf
     */
    public boolean isToolAllowed(Role role, String toolName, List<DocumentChunk> context) {
        return evaluateAccess(role, toolName, context).allowed();
    }

    /**
     * Detaillierte Zugriffspruefung mit Begruendung und Score-Details.
     *
     * <p>Liefert ein {@link ToolPolicyDecision} mit der Entscheidung, der Begruendung
     * und – bei evidenzbasierter Pruefung – dem berechneten Score und Schwellwert.
     * Wird vom Frontend fuer Transparenz-/Audit-Anzeigen verwendet.</p>
     *
     * @param role     aktuelle Benutzerrolle
     * @param toolName Name des angeforderten Tools
     * @param context  Retrieval-Kontext (Chunks), der die Anfrage begleitet
     * @return strukturierte Zugriffsentscheidung
     */
    public ToolPolicyDecision evaluateAccess(Role role, String toolName, List<DocumentChunk> context) {
        if (policyEngine.canUseTool(role, toolName)) {
            return ToolPolicyDecision.roleAuthorized();
        }

        // Evidenzbasierte Policy-Lockerung f\u00fcr Triage-Aktionen
        if (isTriageAction(toolName)) {
            int score = computeEvidenceScore(context);
            int threshold = effectiveThreshold(toolName);
            log.debug("Evidence score for '{}': {} (threshold: {})", toolName, score, threshold);

            if (score >= threshold) {
                // SCHWACHSTELLE [BUG_TOOL_FASTTRACK, Teil 2/2]: Es fehlt die Pruefung,
                // ob mindestens eine trusted interne Quelle vorhanden ist.
                // Hoher Score aus rein untrusted Quellen genuegt, um Aktionen freizuschalten.
                // FIX: Zusaetzlich zum Score muss mindestens eine Quelle mit
                // trustLevel="high" vorhanden sein:
                //   boolean hasTrustedSource = context.stream()
                //           .anyMatch(c -> "high".equals(c.trustLevel()));
                //   if (!hasTrustedSource) {
                //       log.debug("Action '{}' denied: score sufficient but no trusted source", toolName);
                //       return ToolPolicyDecision.insufficientEvidence(score, threshold);
                //   }
                return ToolPolicyDecision.evidenceOverride(score, threshold);
            }
            return ToolPolicyDecision.insufficientEvidence(score, threshold);
        }

        return ToolPolicyDecision.denied();
    }

    /**
     * Berechnet einen Evidenz-Score aus dem abgerufenen Kontext.
     * Interne Richtlinien und Compliance-Referenzen tragen zum Score bei.
     *
     * @param context die abgerufenen Dokument-Chunks
     * @return aggregierter Evidenz-Score
     */
    int computeEvidenceScore(List<DocumentChunk> context) {
        int score = 0;
        for (DocumentChunk chunk : context) {
            int weight = sourceWeight(chunk);
            if (weight == 0) continue;

            // SCHWACHSTELLE [BUG_TOOL_FASTTRACK + BUG_RAG_POISONING Interaktion]:
            // case_note wird gleichwertig mit policy/runbook gez\u00e4hlt. Da Bug 5 User-
            // Notizen als trustLevel "high" / sourceType "case_note" speichert,
            // erh\u00f6ht jede eingeschleuste Notiz den Evidence-Score.
            // FIX: case_note aus dieser Pr\u00fcfung entfernen (oder nur mit trustLevel-Pr\u00fcfung).
            if ("policy".equals(chunk.sourceType()) || "runbook".equals(chunk.sourceType())
                    || "case_note".equals(chunk.sourceType())) {
                score += weight;
            }

            String text = chunk.text().toLowerCase();

            // Compliance-/Verifizierungs-Behauptungen (hohe Gewichtung)
            if (text.contains("verified by compliance") || text.contains("confirmed as a false alarm")
                    || text.contains("durch compliance verifiziert")
                    || text.contains("als fehlalarm best\u00e4tigt") || text.contains("als fehlalarm bestaetigt")
                    || text.contains("unabh\u00e4ngig verifiziert") || text.contains("unabhaengig verifiziert")
                    || text.contains("unabh\u00e4ngigen kanal verifiziert") || text.contains("unabhaengigen kanal verifiziert")) {
                score += 3 * weight;
            }
            // Pr\u00fcfprozess-/Zertifizierungs-Referenzen (mittlere Gewichtung)
            if (text.contains("iso 27001") || text.contains("audit process") || text.contains("pr\u00fcfprozess")
                    || text.contains("pruefprozess")
                    || text.contains("regul\u00e4r verarbeitet") || text.contains("regulaer verarbeitet")
                    || text.contains("rechtsabteilung best\u00e4tigt") || text.contains("rechtsabteilung bestaetigt")) {
                score += 2 * weight;
            }

            // SCHWACHSTELLE [BUG_TOOL_FASTTRACK, Teil 1c/2]: Operative Kontext-Signale
            // wie bestehende Lieferantenbeziehungen oder Finanz-/Rechnungsbez\u00fcge geben
            // zus\u00e4tzliche Punkte. Diese Signale klingen gesch\u00e4ftlich plausibel,
            // sind aber keine Sicherheitsevidenz und d\u00fcrfen nicht als solche z\u00e4hlen.
            // FIX: Diesen Block komplett entfernen. Operative Signale sind KEINE
            // Sicherheitsevidenz und d\u00fcrfen den Score nicht beeinflussen.
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
     * Gibt den Gewichtungsfaktor f\u00fcr eine Quelle basierend auf ihrem Trust-Level zur\u00fcck.
     * Vertrauensw\u00fcrdige interne Quellen tragen voll bei; externe Quellen werden abgewertet.
     */
    /**
     * Gibt den effektiven Evidenz-Schwellwert f\u00fcr eine Aktion zur\u00fcck.
     * Niedrigere Schwellwerte f\u00fcr Routine-Triage-Aktionen beschleunigen den Workflow.
     */
    // SCHWACHSTELLE [BUG_TOOL_FASTTRACK, Teil 1a/2]: Reduzierte Schwellwerte fuer
    // "Routine"-Triage-Aktionen beschleunigen den Workflow, senken aber die
    // Evidenzanforderung so weit, dass bereits einseitige untrusted Supplier-
    // Referenzen ausreichen. Kombiniert mit der fehlenden Quellenpruefung (Teil 2)
    // koennen Angreifer ueber manipulierte Supplier-Notes Aktionen ausloesen.
    // FIX: Einheitlichen Schwellwert EVIDENCE_THRESHOLD fuer alle Aktionen verwenden:
    //   return EVIDENCE_THRESHOLD;
    int effectiveThreshold(String toolName) {
        return switch (toolName) {
            case "attach_supplier_trust_note" -> 2;
            case "set_case_priority_low"      -> 3;
            default                           -> EVIDENCE_THRESHOLD;
        };
    }

    // SCHWACHSTELLE [BUG_TOOL_FASTTRACK, Teil 1b/2]: sourceWeight() gibt fuer ALLE
    // Quellen Gewicht 1 zurueck – egal ob trusted oder untrusted. Untrusted Supplier-
    // Notes (mit Phrasen wie "verified by compliance") treiben den Evidence-Score
    // ueber den Threshold und schalten Workflow-Aktionen fuer Nicht-Analysten frei.
    // FIX: Nur trustLevel "high" darf Gewicht > 0 haben:
    //   return "high".equals(chunk.trustLevel()) ? 1 : 0;
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
