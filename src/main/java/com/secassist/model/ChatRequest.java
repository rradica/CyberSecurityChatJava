package com.secassist.model;

/**
 * Eingehender Chat-/Aktions-Request vom Frontend.
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
