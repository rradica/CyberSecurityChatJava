package com.secassist.model;

/**
 * Strukturierter Request fuer den primaeren Backend-Pfad {@code /api/chat}.
 *
 * <p>Der Record enthaelt bereits alle Informationen, die der
 * {@code ChatOrchestrator} fuer die weitere Verarbeitung benoetigt: Rolle,
 * Fallbezug, Nachricht und gewuenschte Aktion. Er wird sowohl vom Frontend als
 * auch indirekt von der Konversationsschicht verwendet.</p>
 *
 * <p>Durch die klare Trennung zwischen strukturierter Aktion und eigentlicher
 * Freitext-Nachricht bleibt der Ablauf leicht testbar. Gleichzeitig wird damit
 * sichtbar, an welcher Stelle das System noch mit freier Sprache arbeitet und
 * ab wann deterministischer Anwendungscode uebernimmt.</p>
 *
 * @param role    aktuell gewaehlte Rolle (z.B. "employee")
 * @param caseId  ID des aktiven Demo-Falls
 * @param message Benutzernachricht (bei Chat und Workflow)
 * @param action  Aktion: chat, handover, similar_cases, evidence, workflow
 */
public record ChatRequest(
        String role,
        String caseId,
        String message,
        String action
) {}
