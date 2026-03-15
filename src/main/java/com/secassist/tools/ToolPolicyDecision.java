package com.secassist.tools;

/**
 * Ergebnis einer Tool-Zugriffsprüfung mit Begründung.
 *
 * <p>Liefert neben der Entscheidung (erlaubt/verweigert) auch die Begründung
 * und – bei evidenzbasierter Prüfung – den berechneten Score und Schwellwert.
 * Diese Informationen werden im Frontend als Audit-/Transparenzinformation
 * angezeigt.</p>
 *
 * @param allowed           {@code true}, wenn das Tool ausgeführt werden darf
 * @param reason            Begründung: "role_authorized", "evidence_override",
 *                          "insufficient_evidence", "denied"
 * @param evidenceScore     berechneter Evidenz-Score (0 wenn nicht anwendbar)
 * @param evidenceThreshold verwendeter Schwellwert (0 wenn nicht anwendbar)
 */
public record ToolPolicyDecision(
        boolean allowed,
        String reason,
        int evidenceScore,
        int evidenceThreshold
) {
    /** Zugriff durch Rollenberechtigung gewährt. */
    public static ToolPolicyDecision roleAuthorized() {
        return new ToolPolicyDecision(true, "role_authorized", 0, 0);
    }

    /** Zugriff durch Evidenz-Score-Überschreitung gewährt. */
    public static ToolPolicyDecision evidenceOverride(int score, int threshold) {
        return new ToolPolicyDecision(true, "evidence_override", score, threshold);
    }

    /** Zugriff verweigert – Score unter Schwellwert. */
    public static ToolPolicyDecision insufficientEvidence(int score, int threshold) {
        return new ToolPolicyDecision(false, "insufficient_evidence", score, threshold);
    }

    /** Zugriff verweigert – keine Rollenberechtigung, keine Triage-Aktion. */
    public static ToolPolicyDecision denied() {
        return new ToolPolicyDecision(false, "denied", 0, 0);
    }
}
