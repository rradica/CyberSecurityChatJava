package com.secassist.model;

import java.util.Set;

/**
 * Ergebnisobjekt der Intent- und Fall-Erkennung fuer freie Benutzernachrichten.
 *
 * <p>Der Record beschreibt, auf welchen Fall sich eine Nachricht vermutlich
 * bezieht und welcher Arbeitsmodus daraus abgeleitet wurde. Damit bildet er die
 * Bruecke zwischen freier Sprache und den strukturierten Request-Pfaden der
 * Anwendung.</p>
 *
 * <p>Weil LLM-Ausgaben fehlerhaft oder uneinheitlich sein koennen, enthaelt der
 * Typ auch Hilfsmethoden fuer Validierung und Bereinigung. Die Fachlogik kann
 * dadurch mit einem stabileren, vorhersehbaren Objekt weiterarbeiten, statt mit
 * ungesichertem Freitext.</p>
 *
 * @param caseId  erkannte Fall-ID (z.B. "suspicious_supplier_invoice") oder {@code null}
 * @param intent  erkannte Aktion: chat, triage, handover, similar_cases, evidence
 * @param summary kurze Zusammenfassung der Erkennung (fuer Logging/Debug)
 */
public record ConversationIntent(
        String caseId,
        String intent,
        String summary
) {
    /** Bekannte Intent-Werte. */
    public static final Set<String> KNOWN_INTENTS = Set.of(
            "chat", "triage", "handover", "similar_cases", "evidence", "add_note"
    );

    /** Prueft, ob ein Case erkannt wurde. */
    public boolean hasCaseId() {
        return caseId != null && !caseId.isBlank();
    }

    /** Prueft, ob der Intent ein bekannter Wert ist. */
    public boolean hasValidIntent() {
        return intent != null && KNOWN_INTENTS.contains(intent);
    }

    /**
     * Gibt eine bereinigte Kopie zurueck: unbekannter Intent wird zu "chat",
     * leere Strings werden zu {@code null}.
     */
    public ConversationIntent sanitized() {
        String safeCaseId = (caseId != null && !caseId.isBlank()) ? caseId : null;
        String safeIntent = (intent != null && KNOWN_INTENTS.contains(intent))
                ? intent : "chat";
        String safeSummary = summary != null ? summary : "";
        return new ConversationIntent(safeCaseId, safeIntent, safeSummary);
    }
}
