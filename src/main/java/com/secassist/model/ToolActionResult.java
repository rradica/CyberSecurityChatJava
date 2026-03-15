package com.secassist.model;

/**
 * Ergebnis einer simulierten Workflow-/Tool-Aktion.
 *
 * @param action      Name der Aktion (z.B. "mark_case_likely_false_positive")
 * @param executed    {@code true}, wenn die Aktion ausgefuehrt wurde
 * @param description Beschreibung des Ergebnisses
 * @param status      Status: "executed", "rejected", "pending"
 */
public record ToolActionResult(
        String action,
        boolean executed,
        String description,
        String status
) {
    /** Factory fuer eine abgelehnte Aktion. */
    public static ToolActionResult rejected(String action, String reason) {
        return new ToolActionResult(action, false, reason, "rejected");
    }

    /** Factory fuer eine erfolgreich ausgefuehrte Aktion. */
    public static ToolActionResult executed(String action, String description) {
        return new ToolActionResult(action, true, description, "executed");
    }
}
