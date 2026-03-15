package com.secassist.llm;

import java.util.List;

import com.secassist.model.ConversationIntent;
import com.secassist.model.DemoCase;
import com.secassist.model.TriageAssessment;

/**
 * Abstraktion für LLM-Aufrufe.
 *
 * <p>Implementiert als {@link OpenAiChatService} mit echten OpenAI-API-Aufrufen.</p>
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
     * strukturierte Ausgabe unterstützen. {@link OpenAiChatService}
     * überschreibt diese Methode mit strukturierter JSON-Ausgabe.</p>
     *
     * @param systemPrompt Kontext inkl. Fallbeschreibung und Retrieval-Daten
     * @param caseDescription Fallbeschreibung für die Triage
     * @return strukturierte Triage-Bewertung
     */
    default TriageAssessment assessTriage(String systemPrompt, String caseDescription) {
        String reply = chat(systemPrompt, "Bewerte diesen Fall f\u00fcr die Triage: " + caseDescription);
        return new TriageAssessment(
                reply,
                "medium",
                null,
                0.5,
                "Bewertung basierend auf LLM-Freitextantwort."
        );
    }

    /**
     * Lässt eine bestehende Triage-Bewertung noch einmal challengen.
     *
     * <p>Diese zweite Prüfung dient nicht als Sicherheitsgrenze, sondern als
     * Stabilisierung gegen Modellvarianz. Die finale Tool-Freigabe bleibt
     * weiterhin vollständig in deterministischem Anwendungscode.</p>
     *
     * @param systemPrompt      Kontext inkl. Fallbeschreibung und Retrieval-Daten
     * @param caseDescription   Fallbeschreibung für die Triage
     * @param initialAssessment erste LLM-Bewertung
     * @return challengte Triage-Bewertung
     */
    default TriageAssessment reviewTriage(String systemPrompt,
                                          String caseDescription,
                                          TriageAssessment initialAssessment) {
        return initialAssessment != null ? initialAssessment.sanitized() : TriageAssessment.FALLBACK;
    }

    /**
     * Erkennt Intent und Fall aus einer natürlichen Benutzernachricht.
     *
     * <p>Default-Implementierung gibt "chat" ohne Case zurück.
     * {@link OpenAiChatService} überschreibt mit LLM-basierter Erkennung.</p>
     *
     * @param userMessage     die Freitext-Nachricht des Benutzers
     * @param availableCases  die verfügbaren Demo-Fälle für Case-Matching
     * @return erkannter Intent und ggf. Fall-ID
     */
    default ConversationIntent detectIntent(String userMessage, List<DemoCase> availableCases) {
        return new ConversationIntent(null, "chat", "Standard: keine Intent-Erkennung");
    }
}
