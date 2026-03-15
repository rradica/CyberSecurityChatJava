package com.secassist.tools;

/**
 * Ergebnis einer Tool-Zugriffspruefung mit Begruendung.
 *
 * <p>Liefert neben der Entscheidung (erlaubt/verweigert) auch die Begruendung
 * und – bei evidenzbasierter Pruefung – den berechneten Score und Schwellwert.
 * Diese Informationen werden im Frontend als Audit-/Transparenzinformation
 * angezeigt.</p>
 *
 * @param allowed           {@code true}, wenn das Tool ausgefuehrt werden darf
 * @param reason            Begruendung: "role_authorized", "evidence_override",
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
    /** Zugriff durch Rollenberechtigung gewaehrt. */
    public static ToolPolicyDecision roleAuthorized() {
        return new ToolPolicyDecision(true, "role_authorized", 0, 0);
    }

    /** Zugriff durch Evidenz-Score-Ueberschreitung gewaehrt. */
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
