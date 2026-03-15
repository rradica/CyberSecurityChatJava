package com.secassist.model;

import java.util.List;

/**
 * Beschreibt den sicherheitsrelevanten Verarbeitungskontext einer Antwort.
 *
 * <p>Der Kontext wird zusammen mit einer {@link ChatResponse} an das Frontend
 * ausgeliefert und macht transparent, mit welcher Rolle, welcher Aktion und
 * welchen Quellen eine Antwort erzeugt wurde. Damit bleibt nachvollziehbar, ob
 * das System innerhalb der erwarteten Sicherheitsgrenzen gearbeitet hat.</p>
 *
 * <p>Im Workshop ist dieser Record besonders wertvoll, weil er versteckte
 * Fehler an die Oberflaeche bringt: zu breite Kontexte, falsche Quelltypen oder
 * unerwartete Freigaben lassen sich direkt in den Metadaten erkennen, statt nur
 * indirekt aus dem Antworttext zu erraten.</p>
 *
 * @param role             Rolle, mit der die Anfrage verarbeitet wurde
 * @param action           ausgefuehrte Aktion (chat, triage, handover, ...)
 * @param caseId           zugeordnete Fall-ID (oder null)
 * @param retrievedSources Metadaten der verwendeten Retrieval-Quellen
 */
public record SecurityContext(
        String role,
        String action,
        String caseId,
        List<SourceMeta> retrievedSources
) {
    /** Kontext ohne Quellen (z.B. fuer Systemantworten). */
    public static SecurityContext of(String role, String action, String caseId) {
        return new SecurityContext(role, action, caseId, List.of());
    }
}
