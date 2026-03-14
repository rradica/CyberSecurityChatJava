package com.secassist.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;

import com.secassist.model.TriageAssessment;

/**
 * LLM-Service mit echtem OpenAI-API-Aufruf über Spring AI.
 *
 * <p>Wird nur aktiv, wenn {@code secassist.mock-llm=false} und ein
 * gültiger {@code OPENAI_API_KEY} konfiguriert ist.</p>
 */
public class OpenAiChatService implements LlmService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiChatService.class);

    /** Niedrige Temperatur für reproduzierbare Triage-Ergebnisse. */
    private static final double TRIAGE_TEMPERATURE = 0.1;

    private final ChatClient chatClient;

    public OpenAiChatService(ChatClient chatClient) {
        this.chatClient = chatClient;
        log.info("OpenAI ChatService initialized \u2013 using real LLM");
    }

    @Override
    public String chat(String systemPrompt, String userMessage) {
        log.debug("Calling OpenAI with system prompt length={}, user message length={}",
                systemPrompt.length(), userMessage.length());

        try {
            String response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userMessage)
                    .call()
                    .content();
            return response != null ? response : "No response from model.";
        } catch (Exception e) {
            log.error("OpenAI API call failed", e);
            return "Error communicating with the LLM: " + e.getMessage()
                    + "\n\nFallback: Please check your API key or switch to mock mode "
                    + "(set SECASSIST_MOCK_LLM=true).";
        }
    }

    /**
     * Strukturierte Triage-Bewertung über echten OpenAI-Aufruf.
     *
     * <p>Nutzt Spring AI {@code entity()} für JSON-Schema-basierte Ausgabe.
     * Das Modell liefert ein strukturiertes {@link TriageAssessment} statt
     * Freitext. Unbekannte Aktionen werden bereinigt, bei Fehlern greift
     * ein sicherer Fallback.</p>
     *
     * @param systemPrompt Kontext inkl. Fallbeschreibung und Retrieval-Daten
     * @param caseDescription Fallbeschreibung für die Triage
     * @return strukturierte Triage-Bewertung (nie {@code null})
     */
    @Override
    public TriageAssessment assessTriage(String systemPrompt, String caseDescription) {
        log.debug("OpenAI assessTriage called for: {}", caseDescription);

        String triageSystemPrompt = buildTriageSystemPrompt(systemPrompt);

        try {
            TriageAssessment result = chatClient.prompt()
                    .system(triageSystemPrompt)
                    .user("Assess this case for triage: " + caseDescription)
                    .options(OpenAiChatOptions.builder()
                            .temperature(TRIAGE_TEMPERATURE)
                            .build())
                    .call()
                    .entity(TriageAssessment.class);

            if (result == null) {
                log.warn("OpenAI returned null TriageAssessment, using fallback");
                return TriageAssessment.FALLBACK;
            }

            TriageAssessment sanitized = result.sanitized();
            log.debug("Triage result: risk={}, action={}, confidence={}",
                    sanitized.riskLevel(), sanitized.recommendedAction(), sanitized.confidence());
            return sanitized;

        } catch (Exception e) {
            log.error("Structured triage assessment failed, returning safe fallback", e);
            return TriageAssessment.FALLBACK;
        }
    }

    /**
     * Ergänzt den Basis-Systemprompt um klare Triage-Anweisungen.
     *
     * <p>Nennt die erlaubten Aktionswerte, damit das Modell nur bekannte
     * Werte liefert. Spring AI hängt automatisch JSON-Schema-Anweisungen
     * für das Ausgabeformat an.</p>
     */
    private String buildTriageSystemPrompt(String baseSystemPrompt) {
        return baseSystemPrompt + "\n\n"
                + "--- TRIAGE ASSESSMENT INSTRUCTIONS ---\n"
                + "Analyze the case and provide a structured triage assessment.\n\n"
                + "Field guidelines:\n"
                + "- summary: Brief analysis of the case (1-3 sentences)\n"
                + "- riskLevel: One of: low, medium, high, critical\n"
                + "- recommendedAction: One of the following, or null if no action is warranted:\n"
                + "    mark_case_likely_false_positive\n"
                + "    set_case_priority_low\n"
                + "    route_case_to_finance_queue\n"
                + "    attach_supplier_trust_note\n"
                + "- confidence: Number between 0.0 and 1.0\n"
                + "- evidenceAssessment: Brief assessment of evidence quality (1-2 sentences)\n\n"
                + "Rules:\n"
                + "- Only recommend an action if the provided context clearly supports it\n"
                + "- If uncertain, set recommendedAction to null and confidence below 0.5\n"
                + "- Base your assessment solely on the provided context\n"
                + "- You provide analysis only \u2013 you do NOT make security decisions\n";
    }
}
