package com.secassist.model;

import java.util.List;

/**
 * Beschreibt einen vorbereiteten Demo-Fall innerhalb des Workshop-Katalogs.
 *
 * <p>Ein {@code DemoCase} bildet die fachliche Klammer zwischen UI, Fallbriefing,
 * Retrieval-Dokumenten und Similar-Cases-Funktion. Er ist damit der kleinste
 * gemeinsame Nenner, ueber den die Anwendung einen Incident konsistent benennen,
 * anzeigen und weiterverarbeiten kann.</p>
 *
 * <p>Die Felder sind bewusst einfach gehalten: wenige Metadaten, klarer
 * Sichtbarkeitsstatus und referenzierte Dokument-IDs. Dadurch bleibt die App
 * fuer Workshop-Teilnehmerinnen und -Teilnehmer schnell lesbar und leicht
 * erweiterbar.</p>
 *
 * @param id            technische ID (z.B. "suspicious_supplier_invoice")
 * @param title         Anzeigename
 * @param description   Kurzbeschreibung des Falls
 * @param type          Falltyp (phishing, insider_threat, etc.)
 * @param severity      Schweregrad (low, medium, high, critical)
 * @param relatedDocIds IDs der Dokumente, die zum Fall gehoeren
 * @param internal      {@code true}, wenn der Fall nur fuer Security-Analysten sichtbar sein soll
 */
public record DemoCase(
        String id,
        String title,
        String description,
        String type,
        String severity,
        List<String> relatedDocIds,
        boolean internal
) {
    /** Convenience-Konstruktor fuer oeffentliche Faelle. */
    public DemoCase(String id, String title, String description,
                    String type, String severity, List<String> relatedDocIds) {
        this(id, title, description, type, severity, relatedDocIds, false);
    }
}
