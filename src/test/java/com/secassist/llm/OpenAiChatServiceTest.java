package com.secassist.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import com.secassist.model.TriageAssessment;

/**
 * Tests für den OpenAI-LLM-Pfad: Fallback-Verhalten, Sanitization
 * und Default-Interface-Implementierung.
 */
class OpenAiChatServiceTest {

    @Test
    void assessTriageReturnsFallbackWhenChatClientFails() {
        ChatClient failingClient = mock(ChatClient.class);
        when(failingClient.prompt()).thenThrow(new RuntimeException("API unavailable"));

        var service = new OpenAiChatService(failingClient);
        TriageAssessment result = service.assessTriage("system prompt", "case description");

        assertThat(result).isEqualTo(TriageAssessment.FALLBACK);
        assertThat(result.recommendedAction()).isNull();
        assertThat(result.confidence()).isEqualTo(0.0);
    }

    @Test
    void reviewTriageReturnsInitialAssessmentWhenChatClientFails() {
        ChatClient failingClient = mock(ChatClient.class);
        when(failingClient.prompt()).thenThrow(new RuntimeException("API unavailable"));

        var service = new OpenAiChatService(failingClient);
        TriageAssessment initial = new TriageAssessment(
                "Erste Bewertung",
                "high",
                "mark_case_likely_false_positive",
                0.9,
                "Kontext enthält starke, aber potentiell fehlerhafte Evidenz.").sanitized();

        TriageAssessment result = service.reviewTriage("system prompt", "case description", initial);

        assertThat(result).isEqualTo(initial);
    }

    @Test
    void defaultLlmServiceAssessTriageReturnsSafeResult() {
        // Default-Implementierung im Interface: Freitext → null action
        LlmService defaultService = (systemPrompt, userMessage) -> "Some free text response.";

        var result = defaultService.assessTriage("system prompt", "case description");

        assertThat(result).isNotNull();
        assertThat(result.recommendedAction()).isNull();
        assertThat(result.riskLevel()).isEqualTo("medium");
    }

    @Test
    void defaultLlmServiceReviewTriageReturnsSanitizedInitialAssessment() {
        LlmService defaultService = (systemPrompt, userMessage) -> "Some free text response.";
        TriageAssessment initial = new TriageAssessment(
                "Initial",
                "high",
                "mark_case_likely_false_positive",
                1.2,
                "Hinweistext");

        TriageAssessment result = defaultService.reviewTriage("system prompt", "case description", initial);

        assertThat(result.confidence()).isEqualTo(1.0);
        assertThat(result.recommendedAction()).isEqualTo("mark_case_likely_false_positive");
        assertThat(result.riskLevel()).isEqualTo("high");
    }

    @Test
    void fallbackAssessmentDoesNotTriggerWorkflowAction() {
        var fallback = TriageAssessment.FALLBACK;

        // FALLBACK darf KEINE Workflow-Aktion auslösen
        assertThat(fallback.recommendedAction()).isNull();
        assertThat(fallback.hasValidAction()).isFalse();
        // Sanitization ändert nichts am FALLBACK
        assertThat(fallback.sanitized()).isEqualTo(fallback);
    }
}
