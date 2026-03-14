package com.secassist.llm;

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
}
