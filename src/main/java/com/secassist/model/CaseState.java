package com.secassist.model;

/**
 * Zustand eines Falls nach Ausfuehrung von Workflow-Aktionen.
 *
 * <p>Macht den Incident-Effekt sichtbar: Eine scheinbar harmlose
 * Triage-Entscheidung kann den Fall in einen Zustand versetzen,
 * der die Sicherheitsreaktion effektiv unterdrueckt.</p>
 *
 * @param escalationSuppressed Eskalation wurde unterdrueckt (false positive)
 * @param priorityLow          Prioritaet auf LOW gesetzt
 * @param routedToFinance      Fall aus Security-Pipeline entfernt → Finance
 * @param trustNoteAttached    Supplier-Trust-Note angehaengt (reduzierte Pruefung)
 */
public record CaseState(
        boolean escalationSuppressed,
        boolean priorityLow,
        boolean routedToFinance,
        boolean trustNoteAttached
) {
    /** Anfangszustand: keine Effekte aktiv. */
    public static CaseState initial() {
        return new CaseState(false, false, false, false);
    }

    /** Prueft, ob mindestens ein Incident-Effekt aktiv ist. */
    public boolean hasActiveEffects() {
        return escalationSuppressed || priorityLow || routedToFinance || trustNoteAttached;
    }

    /** Erzeugt neuen Zustand mit gesetztem Effekt fuer die gegebene Aktion. */
    public CaseState withActionApplied(String action) {
        return switch (action) {
            case "mark_case_likely_false_positive" ->
                    new CaseState(true, priorityLow, routedToFinance, trustNoteAttached);
            case "set_case_priority_low" ->
                    new CaseState(escalationSuppressed, true, routedToFinance, trustNoteAttached);
            case "route_case_to_finance_queue" ->
                    new CaseState(escalationSuppressed, priorityLow, true, trustNoteAttached);
            case "attach_supplier_trust_note" ->
                    new CaseState(escalationSuppressed, priorityLow, routedToFinance, true);
            default -> this;
        };
    }
}
