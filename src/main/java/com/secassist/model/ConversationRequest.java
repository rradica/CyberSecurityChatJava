package com.secassist.model;

/**
 * Eingehender Request fuer den konversationellen Endpunkt.
 *
 * <p>Im Gegensatz zu {@link ChatRequest} enthaelt dieser primär Rolle,
 * Freitext-Nachricht und optional eine bereits im UI gewaehlte Fall-ID.
 * Die Aktion wird weiterhin vom System erkannt.</p>
 *
 * @param role    aktuell gewaehlte Rolle (z.B. "employee")
 * @param message Freitext-Nachricht des Benutzers
 * @param caseId  optional bereits im UI gewaehlter Demo-Fall
 */
public record ConversationRequest(
        String role,
        String message,
        String caseId
) {
    /** Convenience-Konstruktor fuer bestehenden Code ohne explizite Fall-ID. */
    public ConversationRequest(String role, String message) {
        this(role, message, null);
    }
}
