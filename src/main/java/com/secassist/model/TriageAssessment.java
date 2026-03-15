package com.secassist.model;

import java.util.Set;

/**
 * Strukturierte Triage-Bewertung eines Falls.
 *
 * <p>Ersetzt die fragile Freitext-Erkennung per {@code reply.contains()}.
 * Das LLM (oder der Mock) liefert dieses Objekt direkt, der
 * ChatOrchestrator arbeitet mit den typisierten Feldern.</p>
 *
 * @param summary             Zusammenfassung der Triage-Analyse
 * @param riskLevel           Risikoeinschaetzung: low, medium, high, critical
 * @param recommendedAction   empfohlene Workflow-Aktion (null = keine)
 * @param confidence          Konfidenz der Empfehlung (0.0 – 1.0)
 * @param evidenceAssessment  Bewertung der verfuegbaren Evidenz
 */
public record TriageAssessment(
        String summary,
        String riskLevel,
        String recommendedAction,
        double confidence,
        String evidenceAssessment
) {
    /** Bekannte Workflow-Aktionen, die empfohlen werden duerfen. */
    public static final Set<String> KNOWN_ACTIONS = Set.of(
            "mark_case_likely_false_positive",
            "set_case_priority_low",
            "route_case_to_finance_queue",
            "attach_supplier_trust_note"
    );

    /** Gueltige Risikostufen. */
    public static final Set<String> KNOWN_RISK_LEVELS = Set.of(
            "low", "medium", "high", "critical"
    );

    /** Sicherer Fallback, wenn der Real-LLM-Aufruf fehlschlaegt. */
    public static final TriageAssessment FALLBACK = new TriageAssessment(
            "Automatische Triage-Bewertung nicht verf\u00fcgbar. Manuelle Pr\u00fcfung erforderlich.",
            "medium",
            null,
            0.0,
            "Bewertung konnte nicht automatisch abgeschlossen werden."
    );

    /** Prueft, ob die empfohlene Aktion eine bekannte, gueltige Aktion ist. */
    public boolean hasValidAction() {
        return recommendedAction != null && KNOWN_ACTIONS.contains(recommendedAction);
    }

    /** Prueft, ob die Risikostufe ein bekannter, gueltiger Wert ist. */
    public boolean hasValidRiskLevel() {
        return riskLevel != null && KNOWN_RISK_LEVELS.contains(riskLevel);
    }

    /**
     * Gibt eine bereinigte Kopie zurueck: unbekannte Aktionen werden zu {@code null},
     * ungueltiges riskLevel wird zu {@code "medium"}, Confidence wird auf [0,1]
     * geklemmt, null-Felder erhalten sichere Defaults.
     */
    public TriageAssessment sanitized() {
        String safeAction = (recommendedAction != null && KNOWN_ACTIONS.contains(recommendedAction))
                ? recommendedAction : null;
        String safeRiskLevel = (riskLevel != null && KNOWN_RISK_LEVELS.contains(riskLevel))
                ? riskLevel : "medium";
        double clampedConfidence = Math.max(0.0, Math.min(1.0, confidence));
        return new TriageAssessment(
                summary != null ? summary : "Keine Zusammenfassung verf\u00fcgbar.",
                safeRiskLevel,
                safeAction,
                clampedConfidence,
                evidenceAssessment != null ? evidenceAssessment : "Keine Beweisl\u00e4ge-Bewertung verf\u00fcgbar."
        );
    }
}
