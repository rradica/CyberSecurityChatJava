package com.secassist.model;

import java.util.List;

/**
 * Modelliert den aktuellen fachlichen Zustand eines Falls nach Workflow-Aktionen.
 *
 * <p>Der Fallzustand macht sichtbar, dass sicherheitsrelevante Entscheidungen in
 * SecAssist nicht nur Antworttexte veraendern, sondern spaetere Bearbeitung
 * beeinflussen. Eine heruntergestufte Prioritaet, eine unterdrueckte Eskalation
 * oder ein falsches Routing wirken sich damit direkt auf nachfolgende Anfragen aus.</p>
 *
 * <p>Gerade fuer den Workshop ist dieser Record zentral: Er zeigt die zweite
 * Wirkungsebene eines Bugs. Eine falsche Freigabe endet nicht bei einer
 * problematischen Modellantwort, sondern kippt den Fall in einen objektiv anderen
 * Bearbeitungszustand.</p>
 *
 * @param escalationSuppressed Eskalation wurde unterdrueckt (false positive)
 * @param priorityLow          Prioritaet auf LOW gesetzt
 * @param routedToFinance      Fall aus Security-Pipeline entfernt -> Finance
 * @param trustNoteAttached    Supplier-Trust-Note angehaengt (reduzierte Pruefung)
 * @param queue                aktuelle Arbeitswarteschlange
 * @param priority             fachliche Prioritaetsstufe
 * @param reviewStatus         letzter fachlicher Bearbeitungsstatus
 * @param lastAction           letzte ausgefuehrte Aktion
 * @param lastActor            Rolle des zuletzt handelnden Actors
 * @param lastDecision         sichtbare Kurzbeschreibung der letzten Entscheidungsrichtung
 * @param latestEvidence       kompakte Anzeige der zuletzt wirksamen Evidenzquellen
 */
public record CaseState(
        boolean escalationSuppressed,
        boolean priorityLow,
        boolean routedToFinance,
        boolean trustNoteAttached,
        String queue,
        String priority,
        String reviewStatus,
        String lastAction,
        String lastActor,
        String lastDecision,
        List<String> latestEvidence
) {
    /** Anfangszustand: keine Effekte aktiv. */
    public static CaseState initial() {
        return new CaseState(
                false,
                false,
                false,
                false,
                "security_desk",
                "medium",
                "pending_review",
                "none",
                "system",
                "Noch keine Workflow-Entscheidung sichtbar.",
                List.of()
        );
    }

    /** Prueft, ob mindestens ein Incident-Effekt aktiv ist. */
    public boolean hasActiveEffects() {
        return escalationSuppressed || priorityLow || routedToFinance || trustNoteAttached;
    }

    /** Erzeugt neuen Zustand mit gesetztem Effekt fuer die gegebene Aktion. */
    public CaseState withActionApplied(String action) {
        return withActionApplied(action, "system", "Workflow-Aktion im Falljournal vermerkt.", List.of());
    }

    /**
     * Erzeugt einen neuen Zustand mit sichtbar protokollierter Aktion,
     * Actor-Herkunft und kompakten Evidenzhinweisen.
     */
    public CaseState withActionApplied(String action,
                                       String actor,
                                       String decision,
                                       List<String> evidence) {
        List<String> safeEvidence = evidence != null ? List.copyOf(evidence) : List.of();
        return switch (action) {
            case "mark_case_likely_false_positive" ->
                    new CaseState(
                            true,
                            priorityLow,
                            routedToFinance,
                            trustNoteAttached,
                            "deferred_triage",
                            priorityLow ? priority : "medium",
                            "likely_false_positive",
                            action,
                            actor,
                            decision,
                            safeEvidence
                    );
            case "set_case_priority_low" ->
                    new CaseState(
                            escalationSuppressed,
                            true,
                            routedToFinance,
                            trustNoteAttached,
                            queue,
                            "low",
                            "deprioritized",
                            action,
                            actor,
                            decision,
                            safeEvidence
                    );
            case "route_case_to_finance_queue" ->
                    new CaseState(
                            escalationSuppressed,
                            priorityLow,
                            true,
                            trustNoteAttached,
                            "finance_queue",
                            priority,
                            "rerouted_to_finance",
                            action,
                            actor,
                            decision,
                            safeEvidence
                    );
            case "attach_supplier_trust_note" ->
                    new CaseState(
                            escalationSuppressed,
                            priorityLow,
                            routedToFinance,
                            true,
                            queue,
                            priority,
                            "supplier_trust_attached",
                            action,
                            actor,
                            decision,
                            safeEvidence
                    );
            case "create_handover_draft" ->
                    new CaseState(
                            escalationSuppressed,
                            priorityLow,
                            routedToFinance,
                            trustNoteAttached,
                            queue,
                            priority,
                            "handover_prepared",
                            action,
                            actor,
                            decision,
                            safeEvidence
                    );
            default -> this;
        };
    }
}
