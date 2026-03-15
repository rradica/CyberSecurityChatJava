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
 * {@code BUG_TOOL_FASTTRACK}: Der evidenzbasierte Override ist zu grosszuegig
 * modelliert und kann dadurch Analysten-Aktionen fuer weniger privilegierte
 * Rollen freigeben. Die Logik ist absichtlich kompakt, damit der Fehler schnell
 * verstanden und gezielt behoben werden kann.</p>
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
                // SCHWACHSTELLE [BUG_TOOL_FASTTRACK]: Es fehlt die Pruefung,
                // ob ueberhaupt eine vertrauenswuerdige Quelle vorhanden ist.
                // FIX: Neben dem Score mindestens eine trusted Quelle erzwingen.
                // ERKLAERUNG: Hohe Punktzahl aus untrusted Material darf keine Freigabe erzeugen.
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

              // SCHWACHSTELLE [BUG_TOOL_FASTTRACK]: case_note zaehlt wie policy/runbook.
            // In Kombination mit BUG_RAG_POISONING erhoeht das eingeschleuste Notizen.
            // FIX: case_note aus dem Score entfernen oder nur streng validiert werten.
            // ERKLAERUNG: Operative Notizen sind nicht automatisch belastbare Sicherheitsevidenz.
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

            // SCHWACHSTELLE [BUG_TOOL_FASTTRACK]: Auch operative Signale erhoehen den
            // Score, obwohl sie keine echte Sicherheitsevidenz sind.
            // FIX: Diesen Bonus entfernen.
            // ERKLAERUNG: Geschaeftskontext ist nuetzlich fuer Triage, aber keine Autorisierungsgrundlage.
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
     * SCHWACHSTELLE [BUG_TOOL_FASTTRACK]: Einige Aktionen haben zu niedrige Schwellwerte.
     * FIX: Einheitlichen Schwellwert fuer alle sensitiven Aktionen verwenden.
     * ERKLAERUNG: Niedrige Sondergrenzen machen die Fast-Track-Freigabe zu leicht ausloesbar.
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
     * SCHWACHSTELLE [BUG_TOOL_FASTTRACK]: Alle Quellen zaehlen gleich stark.
     * FIX: Untrusted Quellen deutlich abwerten oder ganz ausschliessen.
     * ERKLAERUNG: Sonst koennen ungepruefte Aussagen denselben Einfluss wie interne Evidenz haben.
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
