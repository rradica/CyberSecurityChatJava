package com.secassist.model;

/**
 * Strukturierte Triage-Bewertung eines Falls.
 *
 * <p>Ersetzt die fragile Freitext-Erkennung per {@code reply.contains()}.
 * Das LLM (oder der Mock) liefert dieses Objekt direkt, der
 * ChatOrchestrator arbeitet mit den typisierten Feldern.</p>
 *
 * @param summary             Zusammenfassung der Triage-Analyse
 * @param riskLevel           Risikoeinschätzung: low, medium, high, critical
 * @param recommendedAction   empfohlene Workflow-Aktion (null = keine)
 * @param confidence          Konfidenz der Empfehlung (0.0 – 1.0)
 * @param evidenceAssessment  Bewertung der verfügbaren Evidenz
 */
public record TriageAssessment(
        String summary,
        String riskLevel,
        String recommendedAction,
        double confidence,
        String evidenceAssessment
) {}
