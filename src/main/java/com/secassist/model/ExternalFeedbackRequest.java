package com.secassist.model;

/**
 * Beschreibt eine eingehende externe Rueckmeldung fuer den freigegebenen Partnerkanal.
 *
 * <p>Der Record modelliert die Daten, die ueber den entsprechenden API-Pfad in
 * einen Fall eingebracht werden koennen: sichtbarer Absender, Herkunftskanal
 * und eigentlicher Rueckmeldungstext. Er bildet damit genau den Input, der im
 * Workshop spaeter fuer Trust-Boundary- und RAG-Poisoning-Szenarien relevant ist.</p>
 *
 * <p>Die Struktur ist bewusst klein gehalten, damit klar bleibt: Schon wenige,
 * scheinbar harmlose Felder reichen aus, um externen Inhalt plausibel in einen
 * internen Bearbeitungskontext einzuschleusen, wenn die nachgelagerte Logik ihn
 * falsch einordnet.</p>
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