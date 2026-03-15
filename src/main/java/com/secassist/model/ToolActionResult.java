package com.secassist.model;

/**
 * Beschreibt das Ergebnis einer simulierten Tool- oder Workflow-Aktion.
 *
 * <p>Der Record dient als fachlich lesbare Rueckmeldung nach einer Aktion und
 * macht sichtbar, ob die Aktion ausgefuehrt oder abgelehnt wurde und welche
 * Wirkung das System dazu meldet. Damit ist er die direkte Verbindung zwischen
 * deterministischer Tool-Entscheidung und sichtbarer Rueckgabe an das Frontend.</p>
 *
 * <p>Weil die Anwendung keine echte Infrastruktur ansteuert, ist dieses Objekt
 * zugleich das wichtigste Trageformat fuer simulierte Incident-Folgen. Es soll
 * sich fuer den Workshop plausibel anfuehlen, ohne unnoetige technische
 * Komplexitaet einzufuehren.</p>
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
