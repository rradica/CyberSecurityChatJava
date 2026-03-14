package com.secassist.llm;

import com.secassist.model.TriageAssessment;

/**
 * Abstraktion für LLM-Aufrufe.
 *
 * <p>Implementiert als {@link MockLlmService} (Default, kein API-Key nötig)
 * oder {@link OpenAiChatService} (echte OpenAI-API).</p>
 */
public interface LlmService {

    /**
     * Sendet einen System-Prompt und eine Benutzernachricht an das LLM.
     *
     * @param systemPrompt Kontext und Anweisungen für das Modell
     * @param userMessage  die eigentliche Benutzerfrage
     * @return Antworttext des Modells
     */
    String chat(String systemPrompt, String userMessage);

    /**
     * Erstellt eine strukturierte Triage-Bewertung für einen Fall.
     *
     * <p>Default-Implementierung für LLM-Provider, die keine native
     * strukturierte Ausgabe unterstützen. Der {@link MockLlmService}
     * überschreibt diese Methode mit deterministischen Antworten.</p>
     *
     * @param systemPrompt Kontext inkl. Fallbeschreibung und Retrieval-Daten
     * @param caseDescription Fallbeschreibung für die Triage
     * @return strukturierte Triage-Bewertung
     */
    default TriageAssessment assessTriage(String systemPrompt, String caseDescription) {
        String reply = chat(systemPrompt, "Assess this case for triage: " + caseDescription);
        return new TriageAssessment(
                reply,
                "medium",
                null,
                0.5,
                "Assessment based on LLM free-text response."
        );
    }
}
