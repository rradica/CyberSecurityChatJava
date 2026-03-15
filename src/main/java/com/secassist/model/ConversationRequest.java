package com.secassist.model;

/**
 * Request fuer den natuerlichsprachlichen Konversationspfad der Anwendung.
 *
 * <p>Im Gegensatz zu {@link ChatRequest} enthaelt dieser Typ primaer Rolle,
 * Freitext-Nachricht und optional eine bereits im UI gewaehlte Fall-ID. Die
 * konkrete Aktion wird erst waehrend der Verarbeitung aus der Nachricht
 * abgeleitet und anschliessend in einen strukturierten Request ueberfuehrt.</p>
 *
 * <p>Der Record bildet damit den bewusst offenen Einstiegspunkt fuer normale
 * Benutzerinteraktion. Gleichzeitig bleibt er klein und transparent, damit klar
 * erkennbar ist, welche Informationen vom Browser kommen und welche spaeter vom
 * System selbst hinzugenommen werden.</p>
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
