package com.secassist.model;

import java.util.Set;

/**
 * Ergebnis der Intent-Erkennung aus einer Freitext-Nachricht.
 *
 * <p>Wird vom LLM (oder Mock) zurückgegeben, wenn eine natürliche
 * Benutzernachricht in eine strukturierte Aktion übersetzt wird.</p>
 *
 * @param caseId  erkannte Fall-ID (z.B. "suspicious_supplier_invoice") oder {@code null}
 * @param intent  erkannte Aktion: chat, triage, handover, similar_cases, evidence
 * @param summary kurze Zusammenfassung der Erkennung (für Logging/Debug)
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

    /** Prüft, ob ein Case erkannt wurde. */
    public boolean hasCaseId() {
        return caseId != null && !caseId.isBlank();
    }

    /** Prüft, ob der Intent ein bekannter Wert ist. */
    public boolean hasValidIntent() {
        return intent != null && KNOWN_INTENTS.contains(intent);
    }

    /**
     * Gibt eine bereinigte Kopie zurück: unbekannter Intent wird zu "chat",
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
