package com.secassist.tools;

/**
 * Ergebnisobjekt fuer eine deterministische Tool-Zugriffspruefung.
 *
 * <p>Der Record transportiert nicht nur die nackte Ja-/Nein-Entscheidung,
 * sondern auch die Begruendung sowie optional Evidenz-Score und Schwellwert.
 * Dadurch koennen Backend, Tests und Frontend dieselbe Entscheidung mit
 * identischem Erklaerungskontext auswerten.</p>
 *
 * <p>Gerade im Workshop ist diese Transparenz wichtig: Teilnehmerinnen und
 * Teilnehmer sehen nicht nur, dass eine Aktion erlaubt oder abgelehnt wurde,
 * sondern auch, ob die Freigabe regulaer oder ueber einen problematischen
 * Evidenz-Override zustande kam.</p>
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

    /** Zugriff verweigert - Score unter Schwellwert. */
    public static ToolPolicyDecision insufficientEvidence(int score, int threshold) {
        return new ToolPolicyDecision(false, "insufficient_evidence", score, threshold);
    }

    /** Zugriff verweigert - keine Rollenberechtigung, keine Triage-Aktion. */
    public static ToolPolicyDecision denied() {
        return new ToolPolicyDecision(false, "denied", 0, 0);
    }
}
