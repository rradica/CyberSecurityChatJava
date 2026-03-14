package com.secassist.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import com.secassist.model.TriageAssessment;

/**
 * Tests für den Real-LLM-Pfad: Fallback-Verhalten, Sanitization und
 * Kompatibilität zwischen Mock- und Real-Pfad.
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
    void mockLlmServiceReturnsValidTriageAssessment() {
        var mock = new MockLlmService();
        var result = mock.assessTriage("system prompt", "Suspicious supplier invoice from ACME");

        assertThat(result).isNotNull();
        assertThat(result.summary()).isNotNull().isNotEmpty();
        assertThat(result.riskLevel()).isNotNull();
        // Mock liefert bekannte Aktionen oder null
        if (result.recommendedAction() != null) {
            assertThat(TriageAssessment.KNOWN_ACTIONS).contains(result.recommendedAction());
        }
    }

    @Test
    void mockLlmServiceReturnsNoActionForAlreadyTriagedCase() {
        var mock = new MockLlmService();
        // System-Prompt enthält "Active Case Modifications" → Fall bereits gekippt
        String systemPrompt = "Mode: workflow\n\n=== Active Case Modifications ===\n"
                + "⚠ Security escalation has been SUPPRESSED for this case.";

        var result = mock.assessTriage(systemPrompt, "Already triaged case");

        assertThat(result.recommendedAction()).isNull();
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
    void fallbackAssessmentDoesNotTriggerWorkflowAction() {
        var fallback = TriageAssessment.FALLBACK;

        // FALLBACK darf KEINE Workflow-Aktion auslösen
        assertThat(fallback.recommendedAction()).isNull();
        assertThat(fallback.hasValidAction()).isFalse();
        // Sanitization ändert nichts am FALLBACK
        assertThat(fallback.sanitized()).isEqualTo(fallback);
    }
}
