package com.secassist.model;

/**
 * Eingehender Request für den konversationellen Endpunkt.
 *
 * <p>Im Gegensatz zu {@link ChatRequest} enthält dieser nur Rolle und
 * Freitext-Nachricht – Case und Aktion werden vom System erkannt.</p>
 *
 * @param role    aktuell gewählte Rolle (z.B. "employee")
 * @param message Freitext-Nachricht des Benutzers
 */
public record ConversationRequest(
        String role,
        String message
) {}
