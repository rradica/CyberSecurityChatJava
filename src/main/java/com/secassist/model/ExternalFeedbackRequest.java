package com.secassist.model;

/**
 * Request-Objekt fuer eingehende externe Rueckmeldungen zu einem Fall.
 *
 * <p>Das Modell simuliert einen kleinen Collaboration-/Reply-Kanal, ueber den
 * externe Partner, Lieferanten oder Dienstleister eine Status- oder
 * Verifikationsrueckmeldung zu einem freigegebenen Fall liefern koennen.</p>
 *
 * @param sender  angezeigter externer Absender, z. B. Lieferant oder Partner
 * @param channel Herkunftskanal, z. B. vendor_reply_portal oder mail_forward
 * @param text    eigentlicher Rueckmeldungstext
 */
public record ExternalFeedbackRequest(
        String sender,
        String channel,
        String text
) {}