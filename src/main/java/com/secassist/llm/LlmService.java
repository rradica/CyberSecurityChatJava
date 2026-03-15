package com.secassist.llm;

import java.util.List;

import com.secassist.model.ConversationIntent;
import com.secassist.model.DemoCase;
import com.secassist.model.TriageAssessment;

/**
 * Abstraktion fuer alle LLM-Aufrufe innerhalb von SecAssist.
 *
 * <p>Die Schnittstelle entkoppelt die fachlichen Services von einer konkreten
 * Modell- oder Provider-Implementierung. Dadurch bleiben
 * {@code ConversationService}, {@code ChatOrchestrator} und andere Komponenten
 * auf ihr jeweiliges Verhalten fokussiert, waehrend die eigentliche
 * Kommunikation mit dem Modell hinter dieser API gekapselt ist.</p>
 *
 * <p>Im aktuellen Projektstand wird die Schnittstelle durch
 * {@link OpenAiChatService} implementiert. Die Default-Methoden liefern kleine,
 * defensive Fallbacks, damit die uebrigen Komponenten auch dann lesbar und
 * testbar bleiben, wenn eine spezialisierte Provider-Implementierung einzelne
 * Faehigkeiten nicht nativ anbietet.</p>
 */
public interface LlmService {

    /**
     * Sendet einen System-Prompt und eine Benutzernachricht an das LLM.
     *
     * @param systemPrompt Kontext und Anweisungen fuer das Modell
     * @param userMessage  die eigentliche Benutzerfrage
     * @return Antworttext des Modells
     */
    String chat(String systemPrompt, String userMessage);

    /**
     * Erstellt eine strukturierte Triage-Bewertung fuer einen Fall.
     *
     * <p>Default-Implementierung fuer LLM-Provider, die keine native
     * strukturierte Ausgabe unterstuetzen. {@link OpenAiChatService}
     * ueberschreibt diese Methode mit strukturierter JSON-Ausgabe.</p>
     *
     * @param systemPrompt Kontext inkl. Fallbeschreibung und Retrieval-Daten
     * @param caseDescription Fallbeschreibung fuer die Triage
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
     * Laesst eine bestehende Triage-Bewertung noch einmal challengen.
     *
     * <p>Diese zweite Pruefung dient nicht als Sicherheitsgrenze, sondern als
     * Stabilisierung gegen Modellvarianz. Die finale Tool-Freigabe bleibt
     * weiterhin vollstaendig in deterministischem Anwendungscode.</p>
     *
     * @param systemPrompt      Kontext inkl. Fallbeschreibung und Retrieval-Daten
     * @param caseDescription   Fallbeschreibung fuer die Triage
     * @param initialAssessment erste LLM-Bewertung
     * @return challengte Triage-Bewertung
     */
    default TriageAssessment reviewTriage(String systemPrompt,
                                          String caseDescription,
                                          TriageAssessment initialAssessment) {
        return initialAssessment != null ? initialAssessment.sanitized() : TriageAssessment.FALLBACK;
    }

    /**
     * Erkennt Intent und Fall aus einer natuerlichen Benutzernachricht.
     *
     * <p>Default-Implementierung gibt "chat" ohne Case zurueck.
     * {@link OpenAiChatService} ueberschreibt mit LLM-basierter Erkennung.</p>
     *
     * @param userMessage     die Freitext-Nachricht des Benutzers
     * @param availableCases  die verfuegbaren Demo-Faelle fuer Case-Matching
     * @return erkannter Intent und ggf. Fall-ID
     */
    default ConversationIntent detectIntent(String userMessage, List<DemoCase> availableCases) {
        return new ConversationIntent(null, "chat", "Standard: keine Intent-Erkennung");
    }
}
