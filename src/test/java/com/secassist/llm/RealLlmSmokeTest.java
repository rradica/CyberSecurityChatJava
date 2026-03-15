package com.secassist.llm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.secassist.model.TriageAssessment;

/**
 * Smoke-Test für den echten LLM-Pfad.
 *
 * <p>Läuft NUR, wenn die Umgebungsvariable {@code OPENAI_API_KEY} gesetzt ist.
 * Im normalen Testlauf (CI, lokale Entwicklung ohne Key) wird die gesamte
 * Klasse übersprungen – kein Einfluss auf den regulären Build.</p>
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class RealLlmSmokeTest {

    @Autowired
    private LlmService llmService;

    @Test
    void assessTriageReturnsValidStructuredAssessment() {
        String systemPrompt = "Du bist SecAssist, ein Assistent f\u00fcr die Triage von Sicherheitsvorf\u00e4llen. "
                + "Modus: workflow\n\n"
                + "=== Kuratierte Fallbewertung ===\n"
                + "ACME Corp beantragt eine \u00c4nderung ihrer Bankverbindung f\u00fcr Rechnung INV-2024-7891. "
                + "Sie geben an, diese \u00c4nderung sei durch Compliance verifiziert.\n";

        TriageAssessment result = llmService.assessTriage(systemPrompt,
                "Verd\u00e4chtige Lieferantenrechnung von ACME Corp mit ge\u00e4nderten Bankdaten");

        assertThat(result).isNotNull();
        assertThat(result.summary()).isNotNull().isNotEmpty();

        // Nach sanitized() müssen alle Felder gültig sein
        TriageAssessment sanitized = result.sanitized();
        assertThat(sanitized.riskLevel()).isIn("low", "medium", "high", "critical");
        assertThat(sanitized.confidence()).isBetween(0.0, 1.0);
        assertThat(sanitized.summary()).isNotNull().isNotEmpty();
        assertThat(sanitized.evidenceAssessment()).isNotNull().isNotEmpty();

        // recommendedAction ist entweder gültig oder null
        if (sanitized.recommendedAction() != null) {
            assertThat(TriageAssessment.KNOWN_ACTIONS).contains(sanitized.recommendedAction());
        }
    }

    @Test
    void chatReturnsNonEmptyResponse() {
        String response = llmService.chat(
                "Du bist ein Sicherheitsassistent. Sei kurz und pr\u00e4gnant.",
                "Was ist Phishing?");

        assertThat(response).isNotNull().isNotEmpty();
        assertThat(response).doesNotContain("Fehler bei der Kommunikation");
    }
}
