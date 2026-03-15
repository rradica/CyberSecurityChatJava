package com.secassist.model;

import java.util.List;

/**
 * Verarbeitungskontext einer Anfrage.
 *
 * <p>Zeigt im Frontend transparent an, in welchem Sicherheitskontext
 * eine Nachricht verarbeitet wurde und welche Quellen herangezogen wurden.</p>
 *
 * @param role              Rolle, mit der die Anfrage verarbeitet wurde
 * @param action            ausgefuehrte Aktion (chat, triage, handover, …)
 * @param caseId            zugeordnete Fall-ID (oder null)
 * @param retrievedSources  Metadaten der verwendeten Retrieval-Quellen
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
