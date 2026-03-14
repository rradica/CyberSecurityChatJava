package com.secassist.model;

import java.util.List;

/**
 * Ein vorbereiteter Demo-Fall für den Workshop.
 *
 * @param id            technische ID (z.B. "suspicious_supplier_invoice")
 * @param title         Anzeigename
 * @param description   Kurzbeschreibung des Falls
 * @param type          Falltyp (phishing, insider_threat, etc.)
 * @param severity      Schweregrad (low, medium, high, critical)
 * @param relatedDocIds IDs der Dokumente, die zum Fall gehören
 * @param internal      {@code true}, wenn der Fall nur für Security-Analysten sichtbar sein soll
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
    /** Convenience-Konstruktor für öffentliche Fälle. */
    public DemoCase(String id, String title, String description,
                    String type, String severity, List<String> relatedDocIds) {
        this(id, title, description, type, severity, relatedDocIds, false);
    }
}
